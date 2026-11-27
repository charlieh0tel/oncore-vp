// Dump per-function instruction streams for cross-version matching.
// Argument: output file.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.scalar.Scalar;
import ghidra.program.model.lang.Register;
import ghidra.program.model.address.Address;
import java.io.*;

public class OncoreFingerprint extends GhidraScript {
    @Override
    public void run() throws Exception {
        try (PrintWriter w = new PrintWriter(new FileWriter(getScriptArgs()[0]))) {
            Listing l = currentProgram.getListing();
            for (Function f : currentProgram.getFunctionManager().getFunctions(true)) {
                w.println("F\t" + f.getEntryPoint().getOffset() + "\t" + f.getName());
                for (Instruction in : l.getInstructions(f.getBody(), true)) {
                    StringBuilder sb = new StringBuilder(in.getMnemonicString());
                    StringBuilder refs = new StringBuilder();
                    for (int i = 0; i < in.getNumOperands(); i++) {
                        sb.append(i == 0 ? " " : ",");
                        for (Object o : in.getOpObjects(i)) {
                            if (o instanceof Register) sb.append(((Register) o).getName());
                            else if (o instanceof Scalar) {
                                long v = ((Scalar) o).getSignedValue();
                                sb.append(Math.abs(v) < 0x200 ? Long.toString(v) : "K");
                                if (Math.abs(v) >= 0x200) refs.append("s" + Long.toHexString(((Scalar) o).getUnsignedValue()) + ";");
                            } else if (o instanceof Address) {
                                sb.append("A");
                            }
                            sb.append(".");
                        }
                    }
                    for (Reference r : in.getReferencesFrom()) {
                        if (r.getReferenceType().isFlow() && !r.getReferenceType().isCall()) continue;
                        refs.append((r.getReferenceType().isCall() ? "c" : "r") + Long.toHexString(r.getToAddress().getOffset()) + ";");
                    }
                    w.println("I\t" + Long.toHexString(in.getAddress().getOffset()) + "\t" + sb + "\t" + refs);
                }
            }
        }
    }
}
