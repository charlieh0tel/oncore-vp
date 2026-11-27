// Defines data types from docs/memory-map.md and applies them: the date
// record (RAM $8EBA), the per-channel record array (RAM $6FBC, 8 x $B6),
// and the CS7 correlator channel registers ($FFE800, 8 x $10; also at the
// $00FFE800 alias).  Field names follow the docs; unknown bytes stay
// undefined.  Run after OncoreNames.java.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;

public class OncoreTypes extends GhidraScript {

    private DataTypeManager dtm;
    private final CategoryPath cat = new CategoryPath("/oncore");

    private DataType add(DataType t) {
        return dtm.addDataType(t, DataTypeConflictHandler.REPLACE_HANDLER);
    }

    private void apply(long addr, DataType t, String name) throws Exception {
        Address a = toAddr(addr);
        if (currentProgram.getMemory().getBlock(a) == null) { println("no memory at " + a); return; }
        clearListing(a, a.add(t.getLength() - 1));
        createData(a, t);
        if (name != null) createLabel(a, name, true, SourceType.USER_DEFINED);
        println(a + " " + t.getName() + (name != null ? " " + name : ""));
    }

    @Override
    public void run() throws Exception {
        dtm = currentProgram.getDataTypeManager();
        DataType u8 = ByteDataType.dataType;
        DataType s8 = SignedByteDataType.dataType;
        DataType u16 = WordDataType.dataType;
        DataType u32 = DWordDataType.dataType;
        DataType dbl = DoubleDataType.dataType;

        // Date record: used by $8EBA and the time routines ($09C81A, $09CE90).
        StructureDataType date = new StructureDataType(cat, "date_rec", 0);
        date.setPackingEnabled(false);
        date.add(u8, "month", null);
        date.add(u8, "day", null);
        date.add(u8, "hour", null);
        date.add(u8, "minute", null);
        date.add(UnsignedShortDataType.dataType, "year", null);
        date.add(dbl, "seconds", null);
        DataType dateT = add(date);
        apply(0x8EBAL, dateT, "date_time");

        // Channel record, $B6 bytes; fields read by msg_Ea_body ($0A9BEC).
        StructureDataType ch = new StructureDataType(cat, "chan_rec", 0xB6);
        ch.setPackingEnabled(false);
        ch.replaceAtOffset(0x00, u8, 1, "sv", "SV id (@@Ea)");
        ch.replaceAtOffset(0x01, s8, 1, "mode", "Channel mode 0-8 (@@Ea)");
        ch.replaceAtOffset(0x02, u8, 1, "flags", "Channel status flags (@@Ea)");
        ch.replaceAtOffset(0x86, u8, 1, "signal", "Signal strength (@@Ea)");
        DataType chT = add(ch);
        apply(0x6FBCL, new ArrayDataType(chT, 8, chT.getLength()), "chan");

        // CS7 correlator channel registers; read and write meanings differ.
        StructureDataType cs7 = new StructureDataType(cat, "cs7_chan", 0x10);
        cs7.setPackingEnabled(false);
        cs7.replaceAtOffset(0x0, u32, 4, "carrier", "R: accumulated carrier phase; W: carrier NCO rate");
        cs7.replaceAtOffset(0x4, u16, 2, "init4", "W: $D0A1 at channel start");
        cs7.replaceAtOffset(0x6, u16, 2, "i_slew", "R: prompt I; W: code-phase slew");
        cs7.replaceAtOffset(0x8, u16, 2, "q_prn", "R: prompt Q; W: PRN G2 tap pair");
        cs7.replaceAtOffset(0xA, u16, 2, "disc_ctl", "R: code discriminator; W: control");
        DataType cs7T = add(cs7);
        ArrayDataType cs7A = new ArrayDataType(cs7T, 8, cs7T.getLength());
        apply(0xFFFFE800L, cs7A, "cs7_chan");
        apply(0x00FFE800L, cs7A, null);

        // Typed globals.
        DataType us = UnsignedShortDataType.dataType;
        DataType ss = ShortDataType.dataType;
        apply(0x819EL, dbl, "gps_seconds");
        apply(0x81D2L, dbl, "clock_drift");
        apply(0x81AEL, us, "gps_week");
        apply(0x81C4L, us, "year_hint");
        apply(0x75BCL, u8, "osc_setting");
        apply(0x75BEL, us, "osc_hz_per_lsb");
        apply(0x75C0L, us, "osc_drift_ref");
        // Per-channel samples copied from CS7 by the level-5 handler ($0805B2).
        apply(0x0214L, new ArrayDataType(ss, 8, 2), "trk_i");
        apply(0x0274L, new ArrayDataType(ss, 8, 2), "trk_q");
        apply(0x0144L, new ArrayDataType(ss, 8, 2), "trk_disc");
        apply(0x0702L, new ArrayDataType(u32, 8, 4), "trk_carrier");
    }
}
