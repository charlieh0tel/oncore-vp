// Report: vector-table targets, the CS7 ($FFE800) addresses touched, and the
// size and callers of the functions that touch them.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.util.*;

public class OncoreVectors extends GhidraScript {
    @Override
    public void run() throws Exception {
        FunctionManager fm = currentProgram.getFunctionManager();
        ReferenceManager rm = currentProgram.getReferenceManager();
        TreeMap<String, List<Integer>> byTarget = new TreeMap<>();
        for (int i = 1; i < 256; i++) {
            long t = currentProgram.getMemory().getInt(toAddr(0x80000 + 4L * i)) & 0xffffffffL;
            byTarget.computeIfAbsent(String.format("%06X", t), k -> new ArrayList<>()).add(i);
        }
        println("vector targets (vector numbers):");
        for (Map.Entry<String, List<Integer>> e : byTarget.entrySet()) {
            Function f = fm.getFunctionAt(toAddr(Long.parseLong(e.getKey(), 16)));
            String v = e.getValue().size() > 12 ? e.getValue().size() + " vectors" : e.getValue().toString();
            println("  " + e.getKey() + " " + (f == null ? "-" : f.getName() + " (" + f.getBody().getNumAddresses() + " B)") + " <- " + v);
        }
        Address s = toAddr(0xFFFFE800L), en = toAddr(0xFFFFEFFFL);
        TreeSet<Long> offs = new TreeSet<>();
        Map<String, Integer> fcount = new TreeMap<>();
        for (AddressIterator it = rm.getReferenceDestinationIterator(new AddressSet(s, en), true); it.hasNext();) {
            Address to = it.next();
            offs.add(to.getOffset() - 0xFFFFE800L);
            for (Reference r : rm.getReferencesTo(to)) {
                Function f = fm.getFunctionContaining(r.getFromAddress());
                fcount.merge(f == null ? "?" : f.getName(), 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (long o : offs) sb.append(String.format("%03X ", o));
        println("CS7 offsets: " + sb);
        println("CS7 refs by function: " + fcount);
        for (String fn : fcount.keySet()) {
            List<Function> fl = getGlobalFunctions(fn);
            if (fl.isEmpty()) continue;
            Function f = fl.get(0);
            Set<String> callers = new TreeSet<>();
            for (Function c : f.getCallingFunctions(monitor)) callers.add(c.getName());
            println("  " + fn + " " + f.getEntryPoint() + " size " + f.getBody().getNumAddresses() + " callers " + callers);
        }
    }
}
