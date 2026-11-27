// 6ch (B1121P1114) copy of tools/ghidra/OncoreFloat.java: same routines,
// 8ch addresses - $3F7E (library at $0ACB54, tools/libsig.py).
// Names the soft double-precision run-time routines and gives them their
// register interfaces (B3640 manual B3640-97001, ch. 11): x in A0:A1, y in
// D0:D1; "p" takes x's address in A1; "pp" also y's address in A0; result
// in D0:D1 (a copy in A0:A1).  add64z adds y (D0:D1) into the double at
// the address in A0.  Run before OncoreStackParameters.java, which leaves
// register signatures alone.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.*;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.symbol.SourceType;
import java.util.*;

public class Oncore6Float extends GhidraScript {

    private Register r(String n) { return currentProgram.getRegister(n); }

    private VariableStorage pair(String hi, String lo) throws Exception {
        return new VariableStorage(currentProgram, r(hi), r(lo));
    }

    private VariableStorage one(String reg) throws Exception {
        return new VariableStorage(currentProgram, r(reg));
    }

    private void define(long addr, String name, List<Parameter> params, DataType ret, VariableStorage retStorage)
            throws Exception {
        Address a = toAddr(addr);
        if (getInstructionAt(a) == null) disassemble(a);
        Function f = getFunctionAt(a);
        if (f == null) f = createFunction(a, name);
        if (f == null) { println("could not create " + name + " at " + a); return; }
        f.setName(name, SourceType.USER_DEFINED);
        ReturnParameterImpl rp = new ReturnParameterImpl(ret, retStorage, currentProgram);
        f.updateFunction(null, rp, params, FunctionUpdateType.CUSTOM_STORAGE, true, SourceType.USER_DEFINED);
        println(a + " " + f.getSignature().getPrototypeString());
    }

    @Override
    public void run() throws Exception {
        DataType d = DoubleDataType.dataType;
        DataType dp = new PointerDataType(d);
        String[][] fam = {
            {"0ACE82", "0ACE86", "0ACE8A", "add64"},
            {"0ACE3E", "0ACE42", "0ACE46", "sub64"},
            {"0ACE2C", "0ACE30", "0ACE34", "sub64r"},
            {"0ADF26", "0ADF2A", "0ADF2E", "mul64"},
            {"0AD532", "0AD536", "0AD53A", "div64"},
            {"0AD520", "0AD524", "0AD528", "div64r"},
        };
        for (String[] f : fam) {
            String n = f[3];
            define(Long.parseLong(f[2], 16), n, List.of(
                new ParameterImpl("x", d, pair("A0", "A1"), currentProgram),
                new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), d, pair("D0", "D1"));
            define(Long.parseLong(f[1], 16), n + "p", List.of(
                new ParameterImpl("x", dp, one("A1"), currentProgram),
                new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), d, pair("D0", "D1"));
            define(Long.parseLong(f[0], 16), n + "pp", List.of(
                new ParameterImpl("x", dp, one("A1"), currentProgram),
                new ParameterImpl("y", dp, one("A0"), currentProgram)), d, pair("D0", "D1"));
        }
        define(0x0ACE70L, "add64z", List.of(
            new ParameterImpl("x", dp, one("A0"), currentProgram),
            new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), d, pair("D0", "D1"));

        // cmp64: x in A0:A1, y in D0:D1, returns -1/0/1 for x <,=,> y in D0;
        // cmp64r swaps them first ($0AD192 exg).
        DataType i = IntegerDataType.dataType;
        DataType u = UnsignedIntegerDataType.dataType;
        DataType fl = FloatDataType.dataType;
        DataType flp = new PointerDataType(fl);
        define(0x0AD196L, "cmp64", List.of(
            new ParameterImpl("x", d, pair("A0", "A1"), currentProgram),
            new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), i, one("D0"));
        define(0x0AD192L, "cmp64r", List.of(
            new ParameterImpl("x", d, pair("D0", "D1"), currentProgram),
            new ParameterImpl("y", d, pair("A0", "A1"), currentProgram)), i, one("D0"));

        // Single precision: x in D0, y in D1, result D0; "z" forms operate
        // on the float at (A0) in place ($0ADC44).
        long[][] f32 = {{0x0ACBF2L, 0x0ACBC2L}, {0x0ACBCEL, 0x0ACBB8L}, {0x0ADC4EL, 0x0ADC44L}, {0x0AD2B6L, 0x0AD2AAL}};
        String[] f32n = {"add32", "sub32", "mul32", "div32"};
        for (int k = 0; k < f32.length; k++) {
            define(f32[k][0], f32n[k], List.of(
                new ParameterImpl("x", fl, one("D0"), currentProgram),
                new ParameterImpl("y", fl, one("D1"), currentProgram)), fl, one("D0"));
            define(f32[k][1], f32n[k] + "z", List.of(
                new ParameterImpl("x", flp, one("A0"), currentProgram),
                new ParameterImpl("y", fl, one("D1"), currentProgram)), fl, one("D0"));
        }

        // Conversions: argument in D0 (D0:D1 for double), result in D0 (D0:D1).
        define(0x0AD7A2L, "dtof", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), fl, one("D0"));
        define(0x0AD8AEL, "dtoi", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), i, one("D0"));
        define(0x0AD96EL, "dtoui", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), u, one("D0"));
        define(0x0AD9F4L, "ftod", List.of(new ParameterImpl("x", fl, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0ADA60L, "itod", List.of(new ParameterImpl("x", i, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0ADADAL, "uitod", List.of(new ParameterImpl("x", u, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0ADB3AL, "itof", List.of(new ParameterImpl("x", i, one("D0"), currentProgram)), fl, one("D0"));
    }
}
