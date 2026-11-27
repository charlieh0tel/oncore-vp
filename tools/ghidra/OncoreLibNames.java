// Names the HP B3640 A.04.10 run-time library routines and data linked into
// the flash, found by tools/libsig.py: ABCPU32.LIB module code matched with
// relocated bytes masked, plus names the matched relocations point at
// (RAM variables, trap, frexp, modf); fabs by position and error code.
// Comment: library module or how found.
// F creates a function; L a label.  A second name at one address is a label.
// Leaves user-named functions and symbols alone (e.g. OncoreFloat's).
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.symbol.*;

public class OncoreLibNames extends GhidraScript {

    private static final String[][] NAMES = {
        {"00CC14", "__fp_status", "L"},  // relocation
        {"00CC16", "__fp_control", "L"},  // relocation
        {"00CC1C", "_errno", "L"},  // relocation
        {"0A02AA", "trap", "F"},  // relocation
        {"0B0B36", "sub32z", "F"},  // faddsub32
        {"0B0B40", "add32z", "F"},  // faddsub32
        {"0B0B4A", "sub32r", "F"},  // faddsub32
        {"0B0B4C", "sub32", "F"},  // faddsub32
        {"0B0B70", "add32", "F"},  // faddsub32
        {"0B0D98", "sub64z", "F"},  // faddsub64
        {"0B0DAA", "sub64rpp", "F"},  // faddsub64
        {"0B0DAE", "sub64rp", "F"},  // faddsub64
        {"0B0DB2", "sub64r", "F"},  // faddsub64
        {"0B0DBC", "sub64pp", "F"},  // faddsub64
        {"0B0DC0", "sub64p", "F"},  // faddsub64
        {"0B0DC4", "sub64", "F"},  // faddsub64
        {"0B0DEE", "add64z", "F"},  // faddsub64
        {"0B0E00", "add64pp", "F"},  // faddsub64
        {"0B0E04", "add64p", "F"},  // faddsub64
        {"0B0E08", "add64", "F"},  // faddsub64
        {"0B1110", "cmp64r", "F"},  // fcmp64
        {"0B1114", "cmp64", "F"},  // fcmp64
        {"0B1228", "div32z", "F"},  // fdiv32
        {"0B1232", "div32r", "F"},  // fdiv32
        {"0B1234", "div32", "F"},  // fdiv32
        {"0B148C", "div64z", "F"},  // fdiv64
        {"0B149E", "div64rpp", "F"},  // fdiv64
        {"0B14A2", "div64rp", "F"},  // fdiv64
        {"0B14A6", "div64r", "F"},  // fdiv64
        {"0B14B0", "div64pp", "F"},  // fdiv64
        {"0B14B4", "div64p", "F"},  // fdiv64
        {"0B14B8", "div64", "F"},  // fdiv64
        {"0B1720", "dtof", "F"},  // dbl_to_flt
        {"0B182C", "dtoi", "F"},  // dbl_to_int
        {"0B18EC", "dtoui", "F"},  // dbl_to_uint
        {"0B1972", "ftod", "F"},  // flt_to_dbl
        {"0B19DE", "itod", "F"},  // int_to_dbl
        {"0B1A58", "uitod", "F"},  // uint_to_dbl
        {"0B1AB8", "itof", "F"},  // int_to_flt
        {"0B1BC2", "mul32z", "F"},  // fmul32
        {"0B1BCC", "mul32", "F"},  // fmul32
        {"0B1E92", "mul64z", "F"},  // fmul64
        {"0B1EA4", "mul64pp", "F"},  // fmul64
        {"0B1EA8", "mul64p", "F"},  // fmul64
        {"0B1EAC", "mul64", "F"},  // fmul64
        {"0B20E8", "norm32", "F"},  // fnorm32
        {"0B212E", "norm64", "F"},  // fnorm64
        {"0B21E8", "__fp_error", "F"},  // fperror
        {"0B21E8", "__fp_errorf", "F"},  // fperror
        {"0B21E8", "__fp_errori", "F"},  // fperror
        {"0B254A", "_abs", "F"},  // abs
        {"0B255A", "_atoi", "F"},  // atoi
        {"0B255A", "_atol", "F"},  // atol
        {"0B25F8", "__ctype", "L"},  // ctype
        {"0B26FA", "_labs", "F"},  // labs
        {"0B270A", "_memcpy", "F"},  // memcpy
        {"0B2792", "_memset", "F"},  // memset
        {"0B2996", "__dbl_to_str", "F"},  // dbl_to_str
        {"0B30E8", "__doprnt", "F"},  // doprnt
        {"0B3E72", "_sprintf", "F"},  // sprintf
        {"0B3EA0", "__swrite", "F"},  // swrite
        {"0B3EDA", "__infinity", "L"},  // data_gen
        {"0B3EE2", "_asin", "F"},  // asin
        {"0B41A0", "_atan", "F"},  // atan
        {"0B4444", "_atan2", "F"},  // atan
        {"0B481A", "_cos", "F"},  // cos
        {"0B4AD4", "_fabs", "F"},  // by position and error code
        {"0B4B28", "_floor", "F"},  // floorceil
        {"0B4B36", "_ceil", "F"},  // floorceil
        {"0B4BFA", "_fmod", "F"},  // fmod
        {"0B4DF8", "_frexp", "F"},  // relocation
        {"0B4E7E", "__hex_NaN", "L"},  // mathmisc
        {"0B4E86", "__hex_NaNf", "L"},  // mathmisc
        {"0B4E8A", "_modf", "F"},  // relocation
        {"0B4F5E", "_sin", "F"},  // sin
        {"0B5226", "_sqrt", "F"},  // sqrt
    };

    private boolean userNamed(Address a) {
        Symbol s = currentProgram.getSymbolTable().getPrimarySymbol(a);
        return s != null && s.getSource() == SourceType.USER_DEFINED;
    }

    @Override
    public void run() throws Exception {
        SymbolTable st = currentProgram.getSymbolTable();
        int named = 0, created = 0, labels = 0, kept = 0;
        Address last = null;
        for (String[] r : NAMES) {
            Address a = toAddr(Long.parseLong(r[0], 16));
            String name = r[1];
            if (!currentProgram.getMemory().contains(a)) {
                println("no memory at " + a + " for " + name);
                continue;
            }
            boolean alias = a.equals(last);
            last = a;
            if (r[2].equals("F") && !alias) {
                if (getInstructionAt(a) == null) disassemble(a);
                Function f = getFunctionAt(a);
                if (f == null) {
                    f = createFunction(a, name);
                    if (f == null) { println("could not create " + name + " at " + a); continue; }
                    created++;
                }
                if (f.getName().equals(name)) continue;
                if (f.getSymbol().getSource() == SourceType.USER_DEFINED) {
                    println("kept " + f.getName() + " at " + a + " (library: " + name + ")");
                    kept++;
                    continue;
                }
                f.setName(name, SourceType.IMPORTED);
                named++;
            } else {
                if (st.getGlobalSymbol(name, a) != null) continue;
                if (userNamed(a) || alias) {
                    st.createLabel(a, name, SourceType.IMPORTED);
                } else {
                    createLabel(a, name, true, SourceType.IMPORTED);
                }
                labels++;
            }
        }
        println(named + " functions named (" + created + " created), " + labels + " labels, " + kept + " user names kept");
    }
}
