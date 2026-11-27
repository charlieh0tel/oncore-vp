// Return types the decompiler misses: functions that leave a result in D0
// but whose signature says void, so their bodies decompile empty.  Keeps
// the parameters.  Run after OncoreStackParameters.java.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.*;
import ghidra.program.model.lang.Register;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.VariableStorage;
import ghidra.program.model.listing.ParameterImpl;
import ghidra.program.model.symbol.SourceType;

public class OncoreFixups extends GhidraScript {
    @Override
    public void run() throws Exception {
        // address, return type
        Object[][] ret = {
            {0x097EE4L, BooleanDataType.dataType},  // gps_parity_check: 1 if the six parity bits match (D0, $09842A)
            {0x0B4AD4L, DoubleDataType.dataType},   // _fabs: result in D0:D1
            {0x0A6B08L, IntegerDataType.dataType},  // rad_to_mas: dtoi result in D0
            {0x0A7CC4L, ByteDataType.dataType},     // nmea_token: token length in D0 ($0A7D08)
            {0x0A69E2L, CharDataType.dataType},     // rx_validate: tail result of $0A643C / $0ADC86
        };
        for (Object[] r : ret) {
            Function f = getFunctionAt(toAddr((Long) r[0]));
            if (f == null) { println("no function at " + Long.toHexString((Long) r[0])); continue; }
            DataType t = (DataType) r[1];
            Register d0 = currentProgram.getRegister("D0");
            VariableStorage st = t.getLength() == 8
                ? new VariableStorage(currentProgram, d0, currentProgram.getRegister("D1"))
                : new VariableStorage(currentProgram,
                    d0.getAddress().add(d0.getMinimumByteSize() - t.getLength()), t.getLength());
            f.setCustomVariableStorage(true);
            f.getReturn().setDataType(t, st, true, SourceType.USER_DEFINED);
            println(f.getEntryPoint() + " " + f.getSignature().getPrototypeString());
        }
        // gps_to_date: 30 bytes of stack arguments; Ghidra found fewer.
        // Callers push week, seconds, year, &local offset, &record, flag
        // (1 = apply UTC), GPS-UTC offset ($09D438).
        Function g = getFunctionAt(toAddr(0x09CE90L));
        if (g != null) {
            DataType u16 = UnsignedShortDataType.dataType, dbl = DoubleDataType.dataType;
            DataType bp = new PointerDataType(ByteDataType.dataType);
            Object[][] ps = {{"week", u16, 4}, {"seconds", dbl, 6}, {"year", u16, 14}, {"local_offset", bp, 16},
                {"rec", bp, 20}, {"apply_utc", ByteDataType.dataType, 24}, {"gps_utc", dbl, 26}};
            java.util.List<ghidra.program.model.listing.Parameter> pl = new java.util.ArrayList<>();
            for (Object[] q : ps) {
                DataType t = (DataType) q[1];
                pl.add(new ParameterImpl((String) q[0], t, new VariableStorage(currentProgram,
                    currentProgram.getAddressFactory().getStackSpace().getAddress((Integer) q[2]), t.getLength()), currentProgram));
            }
            g.updateFunction(null, g.getReturn(), pl, Function.FunctionUpdateType.CUSTOM_STORAGE, true, SourceType.USER_DEFINED);
            println(g.getEntryPoint() + " " + g.getSignature().getPrototypeString());
        }

        // rad_to_mas(double rad): rad at (8,A6), i.e. stack offset 4 at entry.
        Function f = getFunctionAt(toAddr(0x0A6B08L));
        if (f != null && f.getParameterCount() == 0) {
            f.addParameter(new ParameterImpl("rad", DoubleDataType.dataType,
                new VariableStorage(currentProgram, currentProgram.getAddressFactory().getStackSpace().getAddress(4), 8),
                currentProgram), SourceType.USER_DEFINED);
            println(f.getEntryPoint() + " " + f.getSignature().getPrototypeString());
        }
    }
}
