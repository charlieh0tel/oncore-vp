// Report, for each function address given, every call site with its
// containing function and the immediate values pushed just before the call.
// Arguments: function entry addresses (hex).
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.util.*;

public class OncoreCallers extends GhidraScript {
    @Override
    public void run() throws Exception {
        FunctionManager fm = currentProgram.getFunctionManager();
        Listing lst = currentProgram.getListing();
        for (String a : getScriptArgs()) {
            Address t = toAddr(Long.parseLong(a, 16));
            StringBuilder sb = new StringBuilder();
            for (Reference r : currentProgram.getReferenceManager().getReferencesTo(t)) {
                if (!r.getReferenceType().isCall()) continue;
                Function f = fm.getFunctionContaining(r.getFromAddress());
                List<String> pushes = new ArrayList<>();
                Instruction in = lst.getInstructionAt(r.getFromAddress());
                for (int i = 0; i < 4 && in != null; i++) {
                    in = in.getPrevious();
                    if (in == null) break;
                    String s = in.toString();
                    if (s.contains("-(SP)") || s.contains("-(sp)") || s.contains("sp@-")) pushes.add(0, s.replaceAll("\\s+", " "));
                    else break;
                }
                sb.append("\n   ").append(f == null ? "?" : f.getEntryPoint()).append(" @").append(r.getFromAddress()).append(" ").append(pushes);
            }
            println(a + ":" + sb);
        }
    }
}
