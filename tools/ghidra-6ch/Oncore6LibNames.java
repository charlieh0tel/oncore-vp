// 6ch (B1121P1114) copy of tools/ghidra/OncoreLibNames.java; table from
// tools/libsig.py --flash third_party/moto/B1121P1114-flash.bin --java,
// fabs at $0B0B56 (same bytes as the 8ch fabs at $0B4AD4).
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

public class Oncore6LibNames extends GhidraScript {

    private static final String[][] NAMES = {
        {"00BA1C", "__fp_status", "L"},  // relocation
        {"00BA1E", "__fp_control", "L"},  // relocation
        {"00BA24", "_errno", "L"},  // relocation
        {"09CFA6", "trap", "F"},  // relocation
        {"0ACBB8", "sub32z", "F"},  // faddsub32
        {"0ACBC2", "add32z", "F"},  // faddsub32
        {"0ACBCC", "sub32r", "F"},  // faddsub32
        {"0ACBCE", "sub32", "F"},  // faddsub32
        {"0ACBF2", "add32", "F"},  // faddsub32
        {"0ACE1A", "sub64z", "F"},  // faddsub64
        {"0ACE2C", "sub64rpp", "F"},  // faddsub64
        {"0ACE30", "sub64rp", "F"},  // faddsub64
        {"0ACE34", "sub64r", "F"},  // faddsub64
        {"0ACE3E", "sub64pp", "F"},  // faddsub64
        {"0ACE42", "sub64p", "F"},  // faddsub64
        {"0ACE46", "sub64", "F"},  // faddsub64
        {"0ACE70", "add64z", "F"},  // faddsub64
        {"0ACE82", "add64pp", "F"},  // faddsub64
        {"0ACE86", "add64p", "F"},  // faddsub64
        {"0ACE8A", "add64", "F"},  // faddsub64
        {"0AD192", "cmp64r", "F"},  // fcmp64
        {"0AD196", "cmp64", "F"},  // fcmp64
        {"0AD2AA", "div32z", "F"},  // fdiv32
        {"0AD2B4", "div32r", "F"},  // fdiv32
        {"0AD2B6", "div32", "F"},  // fdiv32
        {"0AD50E", "div64z", "F"},  // fdiv64
        {"0AD520", "div64rpp", "F"},  // fdiv64
        {"0AD524", "div64rp", "F"},  // fdiv64
        {"0AD528", "div64r", "F"},  // fdiv64
        {"0AD532", "div64pp", "F"},  // fdiv64
        {"0AD536", "div64p", "F"},  // fdiv64
        {"0AD53A", "div64", "F"},  // fdiv64
        {"0AD7A2", "dtof", "F"},  // dbl_to_flt
        {"0AD8AE", "dtoi", "F"},  // dbl_to_int
        {"0AD96E", "dtoui", "F"},  // dbl_to_uint
        {"0AD9F4", "ftod", "F"},  // flt_to_dbl
        {"0ADA60", "itod", "F"},  // int_to_dbl
        {"0ADADA", "uitod", "F"},  // uint_to_dbl
        {"0ADB3A", "itof", "F"},  // int_to_flt
        {"0ADC44", "mul32z", "F"},  // fmul32
        {"0ADC4E", "mul32", "F"},  // fmul32
        {"0ADF14", "mul64z", "F"},  // fmul64
        {"0ADF26", "mul64pp", "F"},  // fmul64
        {"0ADF2A", "mul64p", "F"},  // fmul64
        {"0ADF2E", "mul64", "F"},  // fmul64
        {"0AE16A", "norm32", "F"},  // fnorm32
        {"0AE1B0", "norm64", "F"},  // fnorm64
        {"0AE26A", "__fp_error", "F"},  // fperror
        {"0AE26A", "__fp_errorf", "F"},  // fperror
        {"0AE26A", "__fp_errori", "F"},  // fperror
        {"0AE5CC", "_abs", "F"},  // abs
        {"0AE5DC", "_atoi", "F"},  // atoi
        {"0AE5DC", "_atol", "F"},  // atol
        {"0AE67A", "__ctype", "L"},  // ctype
        {"0AE77C", "_labs", "F"},  // labs
        {"0AE78C", "_memcpy", "F"},  // memcpy
        {"0AE814", "_memset", "F"},  // memset
        {"0AEA18", "__dbl_to_str", "F"},  // dbl_to_str
        {"0AF16A", "__doprnt", "F"},  // doprnt
        {"0AFEF4", "_sprintf", "F"},  // sprintf
        {"0AFF22", "__swrite", "F"},  // swrite
        {"0AFF5C", "__infinity", "L"},  // data_gen
        {"0AFF64", "_asin", "F"},  // asin
        {"0B0222", "_atan", "F"},  // atan
        {"0B04C6", "_atan2", "F"},  // atan
        {"0B089C", "_cos", "F"},  // cos
        {"0B0BAA", "_floor", "F"},  // floorceil
        {"0B0BB8", "_ceil", "F"},  // floorceil
        {"0B0C7C", "_fmod", "F"},  // fmod
        {"0B0E7A", "_frexp", "F"},  // relocation
        {"0B0F00", "__hex_NaN", "L"},  // mathmisc
        {"0B0F08", "__hex_NaNf", "L"},  // mathmisc
        {"0B0F0C", "_modf", "F"},  // relocation
        {"0B0FE0", "_sin", "F"},  // sin
        {"0B12A8", "_sqrt", "F"},  // sqrt
        {"0B0B56", "_fabs", "F"},  // by position and error code
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
