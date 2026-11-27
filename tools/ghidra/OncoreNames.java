// Applies the name tables names-*.tsv in this directory (columns: address,
// kind, name, note).  Kinds: F function (created if missing), L label,
// S struct field "BASE+OFF" (renames or defines a byte field in the
// struct or struct array at BASE).  Later files win where they disagree:
// files are read in name order with names-core.tsv first.  Replaces
// Ghidra default names and names this script set; leaves other names
// (OncoreFloat, OncoreLibNames) alone.  Run after OncoreLibNames.java.
//@category Oncore

import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class OncoreNames extends GhidraScript {

    private int fn, lb, sf, kept, bad;

    private boolean ours(Symbol s) {
        return s.getSource() == SourceType.DEFAULT || s.getSource() == SourceType.USER_DEFINED
            && s.getParentNamespace().isGlobal() && !protectedNames.contains(s.getName());
    }

    private final Set<String> protectedNames = new HashSet<>();

    @Override
    public void run() throws Exception {
        // Names set by OncoreFloat (USER_DEFINED) must survive.
        for (String n : new String[] {"add64", "sub64", "sub64r", "mul64", "div64", "div64r", "add64z",
                "cmp64", "cmp64r", "add32", "sub32", "mul32", "div32", "add32z", "sub32z", "mul32z", "div32z",
                "dtof", "dtoi", "dtoui", "ftod", "itod", "uitod", "itof"}) {
            protectedNames.add(n); protectedNames.add(n + "p"); protectedNames.add(n + "pp");
        }
        File dir = getSourceFile().getParentFile().getFile(false);
        File[] files = dir.listFiles((d, n) -> n.startsWith("names-") && n.endsWith(".tsv"));
        Arrays.sort(files, Comparator.comparing((File f) -> !f.getName().equals("names-core.tsv"))
            .thenComparing(File::getName));
        for (File f : files) {
            int before = fn + lb + sf;
            for (String line : Files.readAllLines(f.toPath())) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = line.split("\t");
                if (c.length < 3) { bad++; continue; }
                try { apply(c[0].trim(), c[1].trim(), c[2].trim()); }
                catch (Exception e) { println(f.getName() + ": " + line + ": " + e.getMessage()); bad++; }
            }
            println(f.getName() + ": " + (fn + lb + sf - before) + " applied");
        }
        println(fn + " functions, " + lb + " labels, " + sf + " struct fields, " + kept + " kept, " + bad + " bad");
    }

    private void apply(String addr, String kind, String name) throws Exception {
        if (kind.equals("S")) { field(addr, name); return; }
        Address a = toAddr(Long.parseLong(addr, 16));
        if (kind.equals("X")) {
            Function f = getFunctionAt(a);
            if (f != null) {
                removeFunction(f);
                Function p = getFunctionBefore(a);
                if (p != null) ghidra.app.cmd.function.CreateFunctionCmd.fixupFunctionBody(currentProgram, p, monitor);
                println("removed function " + name + " at " + a + (p != null ? ", refit " + p.getName() : ""));
            }
            return;
        }
        if (kind.equals("F")) {
            if (getInstructionAt(a) == null) disassemble(a);
            Function f = getFunctionAt(a);
            if (f == null) f = createFunction(a, name);
            if (f == null) { println("no function at " + a + " for " + name); bad++; return; }
            if (f.getName().equals(name)) { fn++; return; }
            if (ours(f.getSymbol())) { f.setName(name, SourceType.USER_DEFINED); fn++; }
            else kept++;
        } else {
            Symbol s = getSymbolAt(a);
            if (s != null && s.getName().equals(name)) { lb++; return; }
            for (Symbol o : currentProgram.getSymbolTable().getSymbols(a)) {
                if (o.getName().equals(name)) { o.setPrimary(); lb++; return; }
            }
            if (s == null || ours(s)) {
                if (s != null && s.getSource() != SourceType.DEFAULT && getFunctionAt(a) == null) s.setName(name, SourceType.USER_DEFINED);
                else createLabel(a, name, true, SourceType.USER_DEFINED);
                lb++;
            } else { createLabel(a, name, false, SourceType.USER_DEFINED); lb++; }
        }
    }

    private void field(String spec, String name) throws Exception {
        String[] p = spec.split("\\+");
        Address base = toAddr(Long.parseLong(p[0], 16));
        int off = Integer.parseInt(p[1], 16);
        Data d = getDataAt(base);
        if (d == null) { println("no data at " + base + " for field " + name); bad++; return; }
        DataType t = d.getDataType();
        if (t instanceof Array) t = ((Array) t).getDataType();
        if (!(t instanceof Structure)) { println("no struct at " + base + " for field " + name); bad++; return; }
        Structure s = (Structure) t;
        if (off >= s.getLength()) { println("offset past " + s.getName() + " for " + name); bad++; return; }
        DataTypeComponent c = s.getComponentContaining(off);
        if (c != null && c.getOffset() == off && c.getDataType() != DataType.DEFAULT) {
            if (!name.equals(c.getFieldName())) c.setFieldName(name);
        } else {
            s.replaceAtOffset(off, ByteDataType.dataType, 1, name, null);
        }
        sf++;
    }
}
