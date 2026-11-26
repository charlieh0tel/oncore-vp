// Recovers this compiler's PC-relative switch tables.  The switch code is
// extracted from github.com/charlieh0tel/smartclock, tools/ghidra/SmartClockSetup.java
// (main); GPL-3.0 (LICENSE-GPL-3.0).
//
// The switch idiom:
//
//     [cmpi.l #n,Rn | cmpa.l #n,An | subq.l #k,Dn | subi.l #k,Dn]
//     bhi     default
//     move.w  (d8,PC,Rn.w*2),Rn
//     [nop]
//     jmp     (2,PC,Rn.w)
//
// The table starts at the jmp's base and holds word offsets from it.
// After subq/subi #k the index runs -k..0, so the move.w displacement
// points 2k bytes into the table, and there are k + 1 cases; Ghidra takes
// the displacement as the table start and decodes the wrong cases.
// After cmpi #n the index runs 0..n.
//
// For each switch: clear what analysis put over the real table, type it,
// point the jmp at the real cases, disassemble them, rebuild the owning
// function's body and record a decompiler jump-table override.  Repeats
// until a round finds no new switch.  "dry-run" only counts stale labels.
//@category Oncore

import java.util.*;

import ghidra.app.cmd.function.CreateFunctionCmd;
import ghidra.app.script.GhidraScript;
import ghidra.app.util.PseudoDisassembler;
import ghidra.app.util.PseudoInstruction;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSet;
import ghidra.program.model.data.WordDataType;
import ghidra.program.model.listing.Data;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.pcode.JumpTable;
import ghidra.program.model.symbol.*;

public class OncoreSwitches extends GhidraScript {

	private record Switch(Address jmp, Address table, int first, List<Address> targets) {}

	private static final int JMP_PC_INDEX = 0x4efb;
	private static final int MOVEW_PC_INDEX = 0x303b;
	private static final int MOVEW_PC_INDEX_MASK = 0xf1bf;
	private static final int NOP = 0x4e71;
	private static final String DRY_RUN = "dry-run";
	private static final int CMPI_L = 0x0c80;
	private static final int CMPI_W = 0x0c40;
	private static final int CMPI_REGISTER_MASK = 0xfff8;
	private static final int CMPA_L = 0xb1fc;
	private static final int CMPA_W = 0xb0fc;
	private static final int CMPA_REGISTER_MASK = 0xf1ff;
	private static final int BHI_HIGH_BYTE = 0x62;
	private static final int[] BHI_LENGTHS = { 2, 4, 6 };
	private static final int BOUND_SEARCH_BYTES = 16;
	private static final int LINK_A6 = 0x4e56;
	private static final int LINK_LENGTH = 4;
	private static final int MOVEM_L_SAVE = 0x48e7;
	private static final int FRAME_SEARCH_BYTES = 0x1000;
	private static final int CASE_BODY_WINDOW = 0x400;
	private static final int MAX_UNBOUNDED_CASES = 256;
	private static final String SWITCH_NAMESPACE_PREFIX = "switchD_";
	private static final String INDEX_ZERO_SUFFIX = "_index_zero";
	private static final String CASE_LABEL_PREFIX = "caseD_";

	@Override
	protected void run() throws Exception {
		List<Switch> switches = new ArrayList<>();
		List<Address> unrecognized = new ArrayList<>();
		int found;
		do {
			found = recoverSwitches(switches, unrecognized);
			analyzeChanges(currentProgram);
		}
		while (found > 0);
		overrideSwitches(switches);
		println("switches recovered: " + switches.size() + ", unrecognized: " + unrecognized.size());
	}

	private int recoverSwitches(List<Switch> switches, List<Address> unrecognized) throws Exception {
		int added = 0;
		ReferenceManager references = currentProgram.getReferenceManager();
		List<Address> jmps = new ArrayList<>();
		for (Instruction instruction : currentProgram.getListing().getInstructions(true)) {
			Address jmp = instruction.getAddress();
			if ((getShort(jmp) & 0xffff) == JMP_PC_INDEX && !unrecognized.contains(jmp)
					&& switches.stream().noneMatch(s -> s.jmp().equals(jmp))) {
				jmps.add(jmp);
			}
		}
		for (Address jmp : jmps) {
			if (monitor.isCancelled()) {
				break;
			}
			Switch recovered = decodeSwitch(jmp);
			if (recovered == null) {
				println("unrecognized switch shape at " + jmp);
				unrecognized.add(jmp);
				continue;
			}
			for (Reference reference : references.getReferencesFrom(jmp)) {
				if (reference.getReferenceType().isFlow()) {
					references.delete(reference);
				}
			}
			Address tableEnd = recovered.table().add(recovered.targets().size() * 2L - 1);
			removeFunctionsIn(recovered.table(), tableEnd);
			clearListing(recovered.table(), tableEnd);
			for (int i = 0; i < recovered.targets().size(); i++) {
				createData(recovered.table().add(i * 2L), WordDataType.dataType);
			}
			createLabel(recovered.table(), SWITCH_NAMESPACE_PREFIX + jmp, true, SourceType.USER_DEFINED);
			if (recovered.first() != 0) {
				createLabel(recovered.table().add(-recovered.first() * 2L),
					SWITCH_NAMESPACE_PREFIX + jmp + INDEX_ZERO_SUFFIX, true, SourceType.USER_DEFINED);
			}
			for (Address target : recovered.targets()) {
				references.addMemoryReference(jmp, target, RefType.COMPUTED_JUMP, SourceType.USER_DEFINED, 0);
			}
			repairFrom(recovered.targets(), new AddressSet(recovered.table(), tableEnd.add(CASE_BODY_WINDOW)));
			switches.add(recovered);
			added++;
		}
		return added;
	}

	/** Disassembles from the case targets, clearing whatever analysis left over them. */
	private void repairFrom(List<Address> starts, AddressSet window) throws Exception {
		PseudoDisassembler decoder = new PseudoDisassembler(currentProgram);
		Deque<Address> pending = new ArrayDeque<>(starts);
		Set<Address> visited = new HashSet<>();
		while (!pending.isEmpty()) {
			Address address = pending.pop();
			if (!window.contains(address) || (address.getOffset() & 1) != 0 || !visited.add(address)) {
				continue;
			}
			Instruction instruction = getInstructionAt(address);
			if (instruction == null) {
				PseudoInstruction decoded = decoder.disassemble(address);
				if (decoded == null) {
					continue;
				}
				Address last = address.add(decoded.getLength() - 1);
				if (overlapsString(address, last)) {
					continue;
				}
				clearListing(address, last);
				disassemble(address);
				instruction = getInstructionAt(address);
				if (instruction == null) {
					continue;
				}
			}
			pending.addAll(List.of(instruction.getFlows()));
			if (instruction.getFallThrough() != null) {
				pending.add(instruction.getFallThrough());
			}
		}
	}

	private boolean overlapsString(Address start, Address end) {
		for (Data data : currentProgram.getListing().getDefinedData(new AddressSet(start, end), true)) {
			if (data.hasStringValue()) {
				return true;
			}
		}
		Data first = getDataContaining(start);
		return first != null && first.hasStringValue();
	}

	private void removeFunctionsIn(Address start, Address end) {
		for (Function function : currentProgram.getFunctionManager().getFunctions(new AddressSet(start, end), true)) {
			println("removing function " + function.getName() + " inside table at " + start);
			removeFunction(function);
		}
	}

	private Switch decodeSwitch(Address jmp) throws Exception {
		Address table = jmp.add(2 + (byte) getByte(jmp.add(3)));
		Address move = (getShort(jmp.subtract(2)) & 0xffff) == NOP ? jmp.subtract(6) : jmp.subtract(4);
		if ((getShort(move) & MOVEW_PC_INDEX_MASK) != MOVEW_PC_INDEX) {
			return null;
		}
		long bias = table.getOffset() - move.add(2 + (byte) getByte(move.add(3))).getOffset();
		if (bias > 0 || bias % 2 != 0) {
			return null;
		}
		int cases;
		if (bias < 0) {
			if (!hasBranchHigh(move)) {
				return null;
			}
			cases = (int) (-bias / 2) + 1;
		}
		else {
			cases = findCompareBound(move, BOUND_SEARCH_BYTES);
		}
		boolean bounded = cases > 0;
		if (!bounded) {
			cases = MAX_UNBOUNDED_CASES;
		}
		MemoryBlock block = currentProgram.getMemory().getBlock(table);
		List<Address> targets = new ArrayList<>();
		Address lowest = null;
		for (int i = 0; i < cases; i++) {
			Address entry = table.add(i * 2L);
			if (lowest != null && entry.compareTo(lowest) >= 0) {
				if (bounded) {
					println("switch at " + jmp + ": " + cases + " cases overrun case bodies");
					return null;
				}
				break;
			}
			Address target = table.add(getShort(entry));
			if (!block.contains(target) || (target.getOffset() & 1) != 0) {
				if (bounded) {
					return null;
				}
				break;
			}
			targets.add(target);
			if (target.compareTo(table) > 0 && (lowest == null || target.compareTo(lowest) < 0)) {
				lowest = target;
			}
		}
		if (!bounded) {
			println("switch at " + jmp + ": no bound check, " + targets.size() + " cases from table extent");
		}
		return targets.isEmpty() ? null : new Switch(jmp, table, (int) (bias / 2), targets);
	}

	private boolean hasBranchHigh(Address move) throws Exception {
		for (int length : BHI_LENGTHS) {
			if ((getByte(move.subtract(length)) & 0xff) == BHI_HIGH_BYTE) {
				return true;
			}
		}
		return false;
	}

	private int findCompareBound(Address load, int reach) throws Exception {
		for (int back = 2; back <= reach; back += 2) {
			Address at = load.subtract(back);
			int opcode = getShort(at) & 0xffff;
			boolean isLong = (opcode & CMPI_REGISTER_MASK) == CMPI_L || (opcode & CMPA_REGISTER_MASK) == CMPA_L;
			boolean isWord = (opcode & CMPI_REGISTER_MASK) == CMPI_W || (opcode & CMPA_REGISTER_MASK) == CMPA_W;
			if (isLong && ((getByte(at.add(6)) & 0xff) == BHI_HIGH_BYTE)) {
				return getInt(at.add(2)) + 1;
			}
			if (isWord && ((getByte(at.add(4)) & 0xff) == BHI_HIGH_BYTE)) {
				return getShort(at.add(2)) + 1;
			}
		}
		return 0;
	}

	private Function switchFunction(Address jmp) throws Exception {
		Function function = getFunctionContaining(jmp);
		if (function == null) {
			Address frame = findFrame(jmp);
			if (frame == null) {
				return null;
			}
			function = getFunctionAt(frame);
			if (function == null) {
				disassemble(frame);
				function = createFunction(frame, null);
			}
			if (function == null) {
				return null;
			}
		}
		CreateFunctionCmd.fixupFunctionBody(currentProgram, function, monitor);
		return function.getBody().contains(jmp) ? function : null;
	}

	private Address findFrame(Address address) throws Exception {
		MemoryBlock block = currentProgram.getMemory().getBlock(address);
		for (long back = 2; back <= FRAME_SEARCH_BYTES; back += 2) {
			Address at = address.subtract(back);
			if (!block.contains(at)) {
				return null;
			}
			int opcode = getShort(at) & 0xffff;
			if (opcode == LINK_A6) {
				return at;
			}
			if (opcode == MOVEM_L_SAVE) {
				Address link = at.subtract(LINK_LENGTH);
				return block.contains(link) && (getShort(link) & 0xffff) == LINK_A6 ? link : at;
			}
		}
		return null;
	}

	private void overrideSwitches(List<Switch> switches) throws Exception {
		int stale = 0, orphan = 0;
		for (Switch recovered : switches) {
			Function function = switchFunction(recovered.jmp());
			if (function == null) {
				println("switch at " + recovered.jmp() + " is in no function");
				orphan++;
				continue;
			}
			new JumpTable(recovered.jmp(), new ArrayList<>(recovered.targets()), true, 0).writeOverride(function);
			stale += removeStaleCases(recovered);
		}
		println("stale case labels " + (List.of(getScriptArgs()).contains(DRY_RUN) ? "found: " : "removed: ")
			+ stale + "; switches in no function: " + orphan);
	}

	/** Removes case labels analysis put at targets of its late reading of a table. */
	private int removeStaleCases(Switch recovered) throws Exception {
		SymbolTable symbols = currentProgram.getSymbolTable();
		Namespace namespace = symbols.getNamespace(SWITCH_NAMESPACE_PREFIX + recovered.jmp(),
			currentProgram.getGlobalNamespace());
		if (namespace == null) {
			return 0;
		}
		List<Symbol> stale = new ArrayList<>();
		for (Symbol symbol : symbols.getSymbols(namespace)) {
			boolean caseLabel = symbol.getName().startsWith(CASE_LABEL_PREFIX);
			if ((caseLabel && !recovered.targets().contains(symbol.getAddress()))
					|| (!caseLabel && symbol.getSymbolType() == SymbolType.FUNCTION)) {
				stale.add(symbol);
			}
		}
		if (List.of(getScriptArgs()).contains(DRY_RUN)) {
			return stale.size();
		}
		for (Symbol symbol : stale) {
			Function function = getFunctionAt(symbol.getAddress());
			if (function != null && function.getSymbol().equals(symbol)) {
				function.setParentNamespace(currentProgram.getGlobalNamespace());
				function.setName(null, SourceType.DEFAULT);
			}
			else {
				symbol.delete();
			}
		}
		return stale.size();
	}
}
