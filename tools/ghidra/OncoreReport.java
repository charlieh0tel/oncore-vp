// Report: function count, and the functions that reference each external
// chip-select window, the EEPROM and the on-chip modules.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.symbol.*;
import java.util.*;

public class OncoreReport extends GhidraScript {
    @Override
    public void run() throws Exception {
        FunctionManager fm = currentProgram.getFunctionManager();
        println("functions: " + fm.getFunctionCount());
        ReferenceManager rm = currentProgram.getReferenceManager();
        String[] blocks = {"EEPROM", "IO_CS0_2_3", "IO_CS1", "IO_CS6", "IO_CS5", "IO_CS7", "IO_CS0_2_3_24", "IO_CS1_24", "IO_CS6_24", "IO_CS5_24", "IO_CS7_24", "CS9", "CS10"};
        for (String bn : blocks) {
            MemoryBlock b = currentProgram.getMemory().getBlock(bn);
            TreeMap<Long, TreeSet<String>> addrs = new TreeMap<>();
            for (AddressIterator it = rm.getReferenceDestinationIterator(new AddressSet(b.getStart(), b.getEnd()), true); it.hasNext();) {
                Address to = it.next();
                TreeSet<String> fs = addrs.computeIfAbsent(to.getOffset(), k -> new TreeSet<>());
                for (Reference r : rm.getReferencesTo(to)) {
                    Function f = fm.getFunctionContaining(r.getFromAddress());
                    fs.add((f == null ? "?" : f.getName()) + "@" + r.getFromAddress() + ":" + r.getReferenceType());
                }
            }
            println("== " + bn + " " + b.getStart() + ": " + addrs.size() + " addresses referenced");
            int n = 0;
            for (Map.Entry<Long, TreeSet<String>> e : addrs.entrySet()) {
                if (n++ >= 24) { println("   ..."); break; }
                println(String.format("   %06X <- %s", e.getKey(), String.join(", ", e.getValue().stream().limit(4).toList())) + (e.getValue().size() > 4 ? " (+" + (e.getValue().size() - 4) + ")" : ""));
            }
        }
    }
}
