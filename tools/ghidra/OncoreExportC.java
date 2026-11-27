// Decompiles every function to one C file, in address order, and prints
// counts of named and unnamed functions.  Argument: output file.
//@category Oncore

import ghidra.app.decompiler.*;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.SourceType;
import java.io.*;

public class OncoreExportC extends GhidraScript {
    @Override
    public void run() throws Exception {
        File out = new File(getScriptArgs()[0]);
        DecompInterface di = new DecompInterface();
        di.openProgram(currentProgram);
        int n = 0, named = 0, failed = 0;
        try (PrintWriter w = new PrintWriter(new FileWriter(out))) {
            for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
                if (monitor.isCancelled()) break;
                n++;
                if (f.getSymbol().getSource() != SourceType.DEFAULT) named++;
                DecompileResults r = di.decompileFunction(f, 60, monitor);
                w.println("/* " + f.getEntryPoint() + " */");
                if (r.decompileCompleted()) w.println(r.getDecompiledFunction().getC());
                else { failed++; w.println("/* decompile failed: " + r.getErrorMessage() + " */\n"); }
            }
        }
        println(n + " functions, " + named + " named, " + failed + " failed");
    }
}
