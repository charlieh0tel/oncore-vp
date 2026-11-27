// Set up the Oncore VP's running memory map, vectors and on-chip register
// labels after importing a flash image (B4121P1115 8ch or B1121P1114 6ch)
// at $080000 with the 68000:BE:32:CPU32 language.  Arguments: path to the EEPROM dump.
// Map: docs/memory-map.md ("Memory map running").
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.SourceType;
import java.io.File;
import java.io.FileInputStream;

public class OncoreSetup extends GhidraScript {

    // The CPU32 has a 24-bit bus; code reaches $FFxxxx with sign-extended
    // short addresses ($xxxx.W -> $FFFFxxxx).  Put those blocks at the
    // sign-extended address, with byte-mapped mirrors at $00FFxxxx.
    private Address a(long off) {
        return toAddr(off >= 0xFF0000L && off <= 0xFFFFFFL ? off | 0xFF000000L : off);
    }

    private void ram(String name, long start, long len, boolean vol, String comment) throws Exception {
        Memory m = currentProgram.getMemory();
        MemoryBlock b = m.createUninitializedBlock(name, a(start), len, false);
        b.setRead(true); b.setWrite(true); b.setExecute(false);
        b.setVolatile(vol);
        b.setComment(comment);
    }

    private void lab(long addr, String name, String comment) throws Exception {
        createLabel(a(addr), name, true, SourceType.USER_DEFINED);
        if (comment != null) setEOLComment(a(addr), comment);
    }

    private void w(long addr, String name, String comment) throws Exception {
        lab(addr, name, comment);
        createWord(a(addr));
    }

    private void b(long addr, String name, String comment) throws Exception {
        lab(addr, name, comment);
        createByte(a(addr));
    }

    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        Memory m = currentProgram.getMemory();

        MemoryBlock flash = m.getBlock(a(0x80000));
        flash.setName("FLASH");
        flash.setWrite(false); flash.setExecute(true);
        flash.setComment("CSBOOT, 2 x TN28F010, 256 KB (512 KB window, mirrored)");

        ram("RAM", 0x000000, 0x20000, false, "CS8, HM628128 / TC551001, 128K x 8");

        if (args.length > 0) {
            try (FileInputStream in = new FileInputStream(new File(args[0]))) {
                MemoryBlock e = m.createInitializedBlock("EEPROM", a(0x20000), in, 0x2000, monitor, false);
                e.setRead(true); e.setWrite(true); e.setExecute(false);
                e.setComment("CS4, AT28C64B, 8K x 8");
            }
        } else {
            ram("EEPROM", 0x020000, 0x2000, false, "CS4, AT28C64B, 8K x 8");
        }

        ram("CS9", 0x100000, 0x80000, false, "CS9, 512 KB, reads all $FF");
        ram("CS10", 0x180000, 0x80000, false, "CS10, 512 KB, reads all $FF");
        ram("IO_CS0_2_3", 0xFFC800, 0x800, true, "CS0 write, CS2/CS3 read, 16-bit, DS strobe");
        ram("IO_CS1", 0xFFD000, 0x800, true, "CS1 write only, 16-bit, DS strobe");
        ram("IO_CS6", 0xFFD800, 0x800, true, "CS6, 8-bit");
        ram("IO_CS5", 0xFFE000, 0x800, true, "CS5, 16-bit, DS strobe");
        ram("IO_CS7", 0xFFE800, 0x800, true, "CS7, 16-bit");
        ram("ONCHIP", 0xFFF000, 0x1000, true, "MC68331 module registers (MM = 1)");
        String[][] mirrors = {{"IO_CS0_2_3","FFC800"},{"IO_CS1","FFD000"},{"IO_CS6","FFD800"},
            {"IO_CS5","FFE000"},{"IO_CS7","FFE800"},{"ONCHIP","FFF000"}};
        for (String[] mm : mirrors) {
            long off = Long.parseLong(mm[1], 16);
            MemoryBlock mb = m.createByteMappedBlock(mm[0] + "_24", toAddr(off), a(off), 0x800L * (mm[0].equals("ONCHIP") ? 2 : 1), false);
            mb.setRead(true); mb.setWrite(true); mb.setVolatile(true);
            mb.setComment("24-bit alias of " + mm[0]);
        }

        // SIM
        w(0xFFFA00, "SIMCR", "SIM module configuration");
        w(0xFFFA02, "SIMTR", null);
        w(0xFFFA04, "SYNCR", "clock synthesizer control");
        b(0xFFFA07, "RSR", "reset status");
        w(0xFFFA08, "SIMTRE", null);
        b(0xFFFA11, "PORTE0", null);
        b(0xFFFA13, "PORTE1", null);
        b(0xFFFA15, "DDRE", null);
        b(0xFFFA17, "PEPAR", null);
        b(0xFFFA19, "PORTF0", null);
        b(0xFFFA1B, "PORTF1", null);
        b(0xFFFA1D, "DDRF", null);
        b(0xFFFA1F, "PFPAR", null);
        b(0xFFFA21, "SYPCR", "system protection control (write once)");
        w(0xFFFA22, "PICR", "periodic interrupt control");
        w(0xFFFA24, "PITR", "periodic interrupt timer");
        b(0xFFFA27, "SWSR", "software service");
        b(0xFFFA41, "PORTC", null);
        w(0xFFFA44, "CSPAR0", null);
        w(0xFFFA46, "CSPAR1", null);
        w(0xFFFA48, "CSBARBT", null);
        w(0xFFFA4A, "CSORBT", null);
        for (int i = 0; i <= 10; i++) {
            w(0xFFFA4C + 4 * i, "CSBAR" + i, null);
            w(0xFFFA4E + 4 * i, "CSOR" + i, null);
        }
        // QSM
        w(0xFFFC00, "QSMCR", null);
        w(0xFFFC02, "QTEST", null);
        b(0xFFFC04, "QILR", null);
        b(0xFFFC05, "QIVR", null);
        w(0xFFFC08, "SCCR0", "SCI baud: f_sys / (32 x SCBR)");
        w(0xFFFC0A, "SCCR1", null);
        w(0xFFFC0C, "SCSR", "SCI status");
        w(0xFFFC0E, "SCDR", "SCI data (host serial port)");
        b(0xFFFC15, "PORTQS", null);
        b(0xFFFC16, "PQSPAR", null);
        b(0xFFFC17, "DDRQS", null);
        w(0xFFFC18, "SPCR0", null);
        w(0xFFFC1A, "SPCR1", null);
        w(0xFFFC1C, "SPCR2", null);
        b(0xFFFC1E, "SPCR3", null);
        b(0xFFFC1F, "SPSR", null);
        lab(0xFFFD00, "QSPI_RECRAM", "QSPI receive RAM");
        lab(0xFFFD20, "QSPI_TRANRAM", "QSPI transmit RAM");
        lab(0xFFFD40, "QSPI_COMDRAM", "QSPI command RAM");
        // GPT
        w(0xFFF900, "GPTMCR", null);
        w(0xFFF902, "GPTMTR", null);
        w(0xFFF904, "GPTICR", null);
        b(0xFFF906, "DDRGP", null);
        b(0xFFF907, "PORTGP", null);
        b(0xFFF908, "OC1M", null);
        b(0xFFF909, "OC1D", null);
        w(0xFFF90A, "TCNT", "free-running counter, f_sys/4");
        b(0xFFF90C, "PACTL", null);
        b(0xFFF90D, "PACNT", null);
        w(0xFFF90E, "TIC1", null);
        w(0xFFF910, "TIC2", null);
        w(0xFFF912, "TIC3", null);
        w(0xFFF914, "TOC1", null);
        w(0xFFF916, "TOC2", null);
        w(0xFFF918, "TOC3", null);
        w(0xFFF91A, "TOC4", null);
        w(0xFFF91C, "TI4O5", null);
        b(0xFFF91E, "TCTL1", null);
        b(0xFFF91F, "TCTL2", null);
        b(0xFFF920, "TMSK1", null);
        b(0xFFF921, "TMSK2", "GPT prescaler");
        b(0xFFF922, "TFLG1", null);
        b(0xFFF923, "TFLG2", null);
        b(0xFFF924, "CFORC", null);
        b(0xFFF925, "PWMC", null);
        b(0xFFF926, "PWMA", null);
        b(0xFFF927, "PWMB", null);
        w(0xFFF928, "PWMCNT", null);
        w(0xFFF92A, "PWMBUFA", null);

        // Reset vector table at the start of the flash image (seen at $000000
        // at reset): SSP, PC, then exception vectors.
        String[] vnames = {"RESET_SSP", "RESET_PC", "BUS_ERROR", "ADDRESS_ERROR",
            "ILLEGAL", "ZERO_DIVIDE", "CHK", "TRAPV", "PRIVILEGE", "TRACE",
            "LINE_A", "LINE_F", "HW_BREAKPOINT", "RESERVED_13", "FORMAT_ERROR",
            "UNINIT_INT"};
        Pointer32DataType p = new Pointer32DataType();
        for (int i = 0; i < 256; i++) {
            Address va = a(0x80000 + 4L * i);
            String name = i < vnames.length ? "VEC_" + vnames[i]
                : (i == 24 ? "VEC_SPURIOUS" : (i >= 25 && i <= 31) ? "VEC_AUTOVEC" + (i - 24)
                : (i >= 32 && i <= 47) ? "VEC_TRAP" + (i - 32) : "VEC_" + i);
            createLabel(va, name, true, SourceType.USER_DEFINED);
            if (i == 0) { createDWord(va); continue; }
            createData(va, p);
            long t = m.getInt(va) & 0xffffffffL;
            if (t >= 0x80000 && t < 0x100000 && (t & 1) == 0) {
                Address ta = a(t);
                if (getFunctionAt(ta) == null) {
                    disassemble(ta);
                    createFunction(ta, i == 1 ? "reset_entry" : null);
                }
            }
        }
        addEntryPoint(a(m.getInt(a(0x80004)) & 0xffffffffL));  // reset PC
        println("Oncore setup done");
    }
}
