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

public class OncoreFloat extends GhidraScript {

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
            {"0B0E00", "0B0E04", "0B0E08", "add64"},
            {"0B0DBC", "0B0DC0", "0B0DC4", "sub64"},
            {"0B0DAA", "0B0DAE", "0B0DB2", "sub64r"},
            {"0B1EA4", "0B1EA8", "0B1EAC", "mul64"},
            {"0B14B0", "0B14B4", "0B14B8", "div64"},
            {"0B149E", "0B14A2", "0B14A6", "div64r"},
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
        define(0x0B0DEEL, "add64z", List.of(
            new ParameterImpl("x", dp, one("A0"), currentProgram),
            new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), d, pair("D0", "D1"));

        // cmp64: x in A0:A1, y in D0:D1, returns -1/0/1 for x <,=,> y in D0;
        // cmp64r swaps them first ($0B1110 exg).
        DataType i = IntegerDataType.dataType;
        DataType u = UnsignedIntegerDataType.dataType;
        DataType fl = FloatDataType.dataType;
        DataType flp = new PointerDataType(fl);
        define(0x0B1114L, "cmp64", List.of(
            new ParameterImpl("x", d, pair("A0", "A1"), currentProgram),
            new ParameterImpl("y", d, pair("D0", "D1"), currentProgram)), i, one("D0"));
        define(0x0B1110L, "cmp64r", List.of(
            new ParameterImpl("x", d, pair("D0", "D1"), currentProgram),
            new ParameterImpl("y", d, pair("A0", "A1"), currentProgram)), i, one("D0"));

        // Single precision: x in D0, y in D1, result D0; "z" forms operate
        // on the float at (A0) in place ($0B1BC2).
        long[][] f32 = {{0x0B0B70L, 0x0B0B40L}, {0x0B0B4CL, 0x0B0B36L}, {0x0B1BCCL, 0x0B1BC2L}, {0x0B1234L, 0x0B1228L}};
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
        define(0x0B1720L, "dtof", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), fl, one("D0"));
        define(0x0B182CL, "dtoi", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), i, one("D0"));
        define(0x0B18ECL, "dtoui", List.of(new ParameterImpl("x", d, pair("D0", "D1"), currentProgram)), u, one("D0"));
        define(0x0B1972L, "ftod", List.of(new ParameterImpl("x", fl, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0B19DEL, "itod", List.of(new ParameterImpl("x", i, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0B1A58L, "uitod", List.of(new ParameterImpl("x", u, one("D0"), currentProgram)), d, pair("D0", "D1"));
        define(0x0B1AB8L, "itof", List.of(new ParameterImpl("x", i, one("D0"), currentProgram)), fl, one("D0"));
    }
}
