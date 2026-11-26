// Report: for each function that references the EEPROM ($020000-$021FFF),
// the EEPROM offsets it touches (as ranges) and its access types.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.util.*;

public class OncoreEeprom extends GhidraScript {
    @Override
    public void run() throws Exception {
        FunctionManager fm = currentProgram.getFunctionManager();
        ReferenceManager rm = currentProgram.getReferenceManager();
        TreeMap<String, TreeSet<Long>> byF = new TreeMap<>();
        TreeMap<String, TreeSet<Character>> types = new TreeMap<>();
        AddressSet ee = new AddressSet(toAddr(0x20000), toAddr(0x21FFF));
        for (AddressIterator it = rm.getReferenceDestinationIterator(ee, true); it.hasNext();) {
            Address to = it.next();
            for (Reference r : rm.getReferencesTo(to)) {
                Function f = fm.getFunctionContaining(r.getFromAddress());
                String k = f == null ? "?" + r.getFromAddress() : f.getEntryPoint().toString();
                byF.computeIfAbsent(k, x -> new TreeSet<>()).add(to.getOffset() - 0x20000);
                types.computeIfAbsent(k, x -> new TreeSet<>()).add(r.getReferenceType().getName().charAt(0));
            }
        }
        for (Map.Entry<String, TreeSet<Long>> e : byF.entrySet()) {
            StringBuilder sb = new StringBuilder();
            long start = -2, prev = -2;
            for (long o : e.getValue()) {
                if (o != prev + 1) {
                    if (start >= 0) sb.append(start == prev ? String.format("%04X ", start) : String.format("%04X-%04X ", start, prev));
                    start = o;
                }
                prev = o;
            }
            sb.append(start == prev ? String.format("%04X", start) : String.format("%04X-%04X", start, prev));
            Function f = e.getKey().startsWith("?") ? null : fm.getFunctionAt(toAddr(Long.parseLong(e.getKey(), 16)));
            println(e.getKey() + (f == null ? "" : " (" + f.getBody().getNumAddresses() + " B)") + " " + types.get(e.getKey()) + ": " + sb);
        }
    }
}
