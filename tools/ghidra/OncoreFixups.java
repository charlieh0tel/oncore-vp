// Signatures the decompiler gets wrong, applied after
// OncoreStackParameters.java:
// - functions that return a value in D0 (D0:D1 for double) under a void
//   or integer signature, e.g. gps_parity_check, whose body otherwise
//   decompiles empty;
// - functions with more stack arguments than found (gps_to_date: 30
//   bytes, $09D438; the geoid helpers);
// - the B3640 libm routines (doubles on the stack, result in D0:D1) and
//   sprintf (varargs).
// Stack offsets are from SP at entry (return address at 0); char/short
// arguments take a 2-byte slot with the value at its start.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.*;
import ghidra.program.model.listing.Function.FunctionUpdateType;
import ghidra.program.model.symbol.SourceType;
import java.util.*;

public class OncoreFixups extends GhidraScript {

    private final DataType D = DoubleDataType.dataType;
    private final DataType I = IntegerDataType.dataType;
    private final DataType U8 = ByteDataType.dataType;
    private final DataType U16 = UnsignedShortDataType.dataType;

    private VariableStorage ret(DataType t) throws Exception {
        if (t == null || t == VoidDataType.dataType) return VariableStorage.VOID_STORAGE;
        Register d0 = currentProgram.getRegister("D0");
        if (t.getLength() == 8) return new VariableStorage(currentProgram, d0, currentProgram.getRegister("D1"));
        return new VariableStorage(currentProgram, d0.getAddress().add(4 - t.getLength()), t.getLength());
    }

    private VariableStorage stk(int off, DataType t) throws Exception {
        return new VariableStorage(currentProgram,
            currentProgram.getAddressFactory().getStackSpace().getAddress(off), t.getLength());
    }

    // params: name, type, stack offset; null params = keep the parameters.
    private void sig(long addr, DataType rt, Object[][] params, boolean varargs) throws Exception {
        Function f = getFunctionAt(toAddr(addr));
        if (f == null) { println("no function at " + Long.toHexString(addr)); return; }
        f.setCustomVariableStorage(true);
        if (params == null) {
            f.getReturn().setDataType(rt, ret(rt), true, SourceType.USER_DEFINED);
        } else {
            List<Parameter> pl = new ArrayList<>();
            for (Object[] q : params) {
                DataType t = (DataType) q[1];
                pl.add(new ParameterImpl((String) q[0], t, stk((Integer) q[2], t), currentProgram));
            }
            f.updateFunction(null, new ReturnParameterImpl(rt, ret(rt), currentProgram), pl,
                FunctionUpdateType.CUSTOM_STORAGE, true, SourceType.USER_DEFINED);
        }
        if (varargs) f.setVarArgs(true);
        println(f.getEntryPoint() + " " + f.getSignature().getPrototypeString());
    }

    @Override
    public void run() throws Exception {
        DataType bp = new PointerDataType(U8);
        DataType cp = new PointerDataType(CharDataType.dataType);
        DataType dp = new PointerDataType(D);
        DataType ip = new PointerDataType(I);

        // Return type only.
        sig(0x097EE4L, BooleanDataType.dataType, null, false);  // gps_parity_check: 1 if the parity bits match ($09842A)
        sig(0x0A7CC4L, U8, null, false);                       // nmea_token: token length ($0A7D08)
        sig(0x0A69E2L, CharDataType.dataType, null, false);    // rx_validate: tail result of $0A643C / $0ADC86

        // Full signatures.
        sig(0x0A6B08L, I, new Object[][] {{"rad", D, 4}}, false);  // rad_to_mas: dtoi result
        sig(0x09CE90L, VoidDataType.dataType, new Object[][] {
            {"week", U16, 4}, {"seconds", D, 6}, {"year", U16, 14}, {"local_offset", bp, 16},
            {"rec", bp, 20}, {"apply_utc", U8, 24}, {"gps_utc", D, 26}}, false);
        sig(0x09E66AL, D, new Object[][] {{"sel", U8, 4}, {"idx", I, 6}, {"x", D, 10}}, false);  // geoid_grid_value
        sig(0x09E71CL, I, new Object[][] {{"sel", U8, 4}, {"x", D, 6}}, false);                  // geoid_grid_index

        // geoid_height(datum_rec, llh_rec) by value: 42 + 24 bytes ($08BF24).
        DataType datum = currentProgram.getDataTypeManager().getDataType(new CategoryPath("/oncore"), "datum_rec");
        DataType llh = currentProgram.getDataTypeManager().getDataType(new CategoryPath("/oncore"), "llh_rec");
        if (datum != null && llh != null)
            sig(0x09E310L, D, new Object[][] {{"datum", datum, 4}, {"pos", llh, 46}}, false);

        // libm: double f(double x) etc.
        long[] one = {0x0B4F5EL, 0x0B481AL, 0x0B5226L, 0x0B41A0L, 0x0B3EE2L, 0x0B4AD4L, 0x0B4B28L, 0x0B4B36L};
        for (long a : one) sig(a, D, new Object[][] {{"x", D, 4}}, false);  // sin cos sqrt atan asin fabs floor ceil
        sig(0x0B4444L, D, new Object[][] {{"y", D, 4}, {"x", D, 12}}, false);  // atan2
        sig(0x0B4BFAL, D, new Object[][] {{"x", D, 4}, {"y", D, 12}}, false);  // fmod
        sig(0x0B4DF8L, D, new Object[][] {{"x", D, 4}, {"exp", ip, 12}}, false);  // frexp
        sig(0x0B4E8AL, D, new Object[][] {{"x", D, 4}, {"ip", dp, 12}}, false);   // modf
        sig(0x0B3E72L, I, new Object[][] {{"buf", cp, 4}, {"fmt", cp, 8}}, true); // sprintf
    }
}
