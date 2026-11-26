// Turns off Decompiler Switch Analysis before auto-analysis; OncoreSwitches.java
// recovers the switches afterwards.  From github.com/charlieh0tel/smartclock,
// tools/ghidra/NoSwitch.java; GPL-3.0 (LICENSE-GPL-3.0).
//@category Oncore
import ghidra.app.script.GhidraScript;

public class OncoreNoSwitch extends GhidraScript {
	@Override
	protected void run() throws Exception {
		setAnalysisOption(currentProgram, "Decompiler Switch Analysis", "false");
	}
}
