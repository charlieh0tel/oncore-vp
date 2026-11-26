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
    }
}
