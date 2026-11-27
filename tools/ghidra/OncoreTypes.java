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
import java.io.File;
import java.nio.file.Files;

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

        // Per-channel records found by the tracking pass (fields from
        // names-tracking.tsv via OncoreNames.java).  The arrays tile RAM
        // exactly: $CF6-$E35, $E36-$1105, $275C-$27BB, $27BC-$2A1B,
        // $7A3C-$819B; @@Tc/@@Td snapshot $6347-$641B.
        long[][] recs = {{0xCF6L, 0x28, 8}, {0xE36L, 0x5A, 8}, {0x1106L, 0x3C, 1},
            {0x275CL, 0x0C, 8}, {0x27BCL, 0x2C, 8}, {0x7A3CL, 0xEC, 8},
            {0x6347L, 0x0C, 1}, {0x6353L, 0x07, 8}, {0x638BL, 0x12, 8}};
        String[] recn = {"search_rec", "navdata_rec", "search_ctl", "chan_cmd", "chan_report", "eph_rec", "test_hdr", "test_rec", "test_chan_rec"};
        String[] recl = {"search", "navdata", "search_ctl", "chan_cmd", "chan_report", "eph", "test_hdr", "test_sv", "test_chan"};
        for (int k = 0; k < recs.length; k++) {
            StructureDataType st = new StructureDataType(cat, recn[k], (int) recs[k][1]);
            st.setPackingEnabled(false);
            DataType t = add(st);
            apply(recs[k][0], recs[k][2] == 1 ? t : new ArrayDataType(t, (int) recs[k][2], t.getLength()), recl[k]);
        }

        // Records found by the navigation pass.  SV-indexed arrays start at
        // the address of element 0 (unused) so that x[sv] indexes directly.
        long[][] nav = {{0x116AL, 0x88, 33}, {0x82D8L, 0x50, 33}, {0x8FDAL, 0x58, 38},
            {0x641CL, 0x174, 8}};
        String[] navn = {"alm_exp", "eph_collect", "eph_store", "svst"};
        for (int k = 0; k < nav.length; k++) {
            StructureDataType st = new StructureDataType(cat, navn[k] + "_rec", (int) nav[k][1]);
            st.setPackingEnabled(false);
            DataType t = add(st);
            apply(nav[k][0], new ArrayDataType(t, (int) nav[k][2], t.getLength()), navn[k]);
        }
        // Visible-list / prediction banks ($B14) and almanac buffers ($374).
        StructureDataType pb = new StructureDataType(cat, "pred_bank", 0xB14);
        pb.setPackingEnabled(false);
        DataType pbT = add(pb);
        apply(0x37A2L, pbT, null);
        apply(0x42B6L, pbT, null);
        apply(0x4DCAL, pbT, null);
        StructureDataType ab = new StructureDataType(cat, "alm_buf", 0x374);
        ab.setPackingEnabled(false);
        DataType abT = add(ab);
        apply(0x58DEL, abT, null);
        apply(0x5C52L, abT, null);
        apply(0x5FC6L, abT, null);

        // Navigation matrices (doubles): normal matrix, geometry rows, covariance.
        DataType row4 = new ArrayDataType(dbl, 4, 8);
        apply(0x0984L, new ArrayDataType(row4, 4, 32), null);
        apply(0x0A04L, new ArrayDataType(row4, 8, 32), null);
        apply(0x369CL, new ArrayDataType(row4, 4, 32), null);

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

        // Small records used by types-globals.tsv.
        StructureDataType dg = new StructureDataType(cat, "dgps_rec", 14);
        dg.setPackingEnabled(false);
        dg.replaceAtOffset(0, u8, 1, "valid", null);
        dg.replaceAtOffset(2, ShortDataType.dataType, 2, "rrc", "range-rate correction");
        dg.replaceAtOffset(4, u16, 2, "iode", null);
        dg.replaceAtOffset(6, IntegerDataType.dataType, 4, "prc", "pseudorange correction");
        dg.replaceAtOffset(10, IntegerDataType.dataType, 4, "time", null);
        add(dg);
        StructureDataType cu = new StructureDataType(cat, "chan_use_rec", 4);
        cu.setPackingEnabled(false);
        cu.replaceAtOffset(0, u8, 1, "use_pos", null);
        cu.replaceAtOffset(1, u8, 1, "use_pr", null);
        cu.replaceAtOffset(2, u8, 1, "has_time", null);
        add(cu);
        StructureDataType ut = new StructureDataType(cat, "utc_rec", 14);
        ut.setPackingEnabled(false);
        String[] un = {"dt_ls", "dt_lsf", "tot", "wn_t", "wn_lsf", "dn"};
        for (int k = 0; k < un.length; k++) ut.replaceAtOffset(k, u8, 1, un[k], null);
        ut.replaceAtOffset(6, IntegerDataType.dataType, 4, "a0", "2^-30 s");
        ut.replaceAtOffset(10, IntegerDataType.dataType, 4, "a1", "2^-50 s/s");
        add(ut);
        StructureDataType dr = new StructureDataType(cat, "datum_rec", 42);
        dr.setPackingEnabled(false);
        String[] dn = {"dx", "dy", "dz", "inv_f", "a"};
        for (int k = 0; k < dn.length; k++) dr.replaceAtOffset(8 * k, dbl, 8, dn[k], null);
        dr.replaceAtOffset(40, u8, 1, "number", "datum number (49 = WGS-84)");
        add(dr);
        StructureDataType lr = new StructureDataType(cat, "llh_rec", 24);
        lr.setPackingEnabled(false);
        lr.replaceAtOffset(0, dbl, 8, "h", null);
        lr.replaceAtOffset(8, dbl, 8, "lat", "rad");
        lr.replaceAtOffset(16, dbl, 8, "lon", "rad");
        add(lr);
        globalsTable();

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

    // types-globals.tsv: address, type.  Types: u8 s8 u16 s16 u32 s32 float
    // double char ptr, or a data type name in /oncore; with [n] suffixes.
    private void globalsTable() throws Exception {
        File f = new File(getSourceFile().getParentFile().getFile(false), "types-globals.tsv");
        if (!f.exists()) return;
        int n = 0;
        for (String line : Files.readAllLines(f.toPath())) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t");
            DataType t = parse(c[1].trim());
            if (t == null) { println("unknown type " + c[1]); continue; }
            Address a = toAddr(Long.parseLong(c[0].trim(), 16));
            clearListing(a, a.add(t.getLength() - 1));
            createData(a, t);
            n++;
        }
        println("types-globals.tsv: " + n + " applied");
    }

    private DataType parse(String spec) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\w+)((?:\\[\\d+\\])*)$").matcher(spec);
        if (!m.matches()) return null;
        DataType t;
        switch (m.group(1)) {
            case "u8": t = ByteDataType.dataType; break;
            case "s8": t = SignedByteDataType.dataType; break;
            case "char": t = CharDataType.dataType; break;
            case "u16": t = UnsignedShortDataType.dataType; break;
            case "s16": t = ShortDataType.dataType; break;
            case "u32": t = UnsignedIntegerDataType.dataType; break;
            case "s32": t = IntegerDataType.dataType; break;
            case "float": t = FloatDataType.dataType; break;
            case "double": t = DoubleDataType.dataType; break;
            case "ptr": t = new PointerDataType(DataType.DEFAULT, 4); break;
            default: t = dtm.getDataType(cat, m.group(1));
        }
        if (t == null) return null;
        java.util.List<Integer> dims = new java.util.ArrayList<>();
        java.util.regex.Matcher d = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(m.group(2));
        while (d.find()) dims.add(Integer.parseInt(d.group(1)));
        for (int k = dims.size() - 1; k >= 0; k--) t = new ArrayDataType(t, dims.get(k), t.getLength());
        return t;
    }
}
