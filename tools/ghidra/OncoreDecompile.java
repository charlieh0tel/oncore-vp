// Decompile functions to C files.  Arguments: output directory, then
// function entry addresses (hex) or "xref:ADDR[-ADDR]" to also list who
// references an address range.
//@category Oncore

import ghidra.app.decompiler.*;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class OncoreDecompile extends GhidraScript {
    @Override
    public void run() throws Exception {
        String[] args = getScriptArgs();
        File out = new File(args[0]);
        out.mkdirs();
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        FunctionManager fm = currentProgram.getFunctionManager();
        ReferenceManager rm = currentProgram.getReferenceManager();
        try (PrintWriter xw = new PrintWriter(new FileWriter(new File(out, "xrefs.txt"), true))) {
            for (int i = 1; i < args.length; i++) {
                String a = args[i];
                if (a.startsWith("xref:")) {
                    String[] r = a.substring(5).split("-");
                    Address s = toAddr(Long.parseLong(r[0], 16));
                    Address e = toAddr(Long.parseLong(r.length > 1 ? r[1] : r[0], 16));
                    for (AddressIterator it = rm.getReferenceDestinationIterator(new AddressSet(s, e), true); it.hasNext();) {
                        Address to = it.next();
                        TreeSet<String> fs = new TreeSet<>();
                        for (Reference ref : rm.getReferencesTo(to)) {
                            Function f = fm.getFunctionContaining(ref.getFromAddress());
                            fs.add((f == null ? "?" : f.getEntryPoint().toString()) + ":" + ref.getReferenceType().getName().charAt(0));
                        }
                        xw.println(to + " <- " + String.join(" ", fs));
                    }
                    continue;
                }
                Function f = fm.getFunctionAt(toAddr(Long.parseLong(a, 16)));
                if (f == null) { println("no function at " + a); continue; }
                DecompileResults res = di.decompileFunction(f, 600, monitor);
                String c = res.decompileCompleted() ? res.getDecompiledFunction().getC() : ("// failed: " + res.getErrorMessage());
                try (PrintWriter w = new PrintWriter(new File(out, f.getEntryPoint() + ".c"))) { w.print(c); }
                println(f.getEntryPoint() + " " + f.getName() + " -> " + c.length() + " chars");
            }
        }
        di.dispose();
    }
}
