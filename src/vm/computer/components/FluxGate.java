package vm.computer.components;

import org.json.JSONObject;
import vm.computer.Machine;

public class FluxGate extends ComponentBase{
    public int highFlow;
    public int lowFlow;

    private boolean Override = false;
    public FluxGate(Machine machine, String address, int highFlow, int lowFlow) {
        super(machine, address, "flux_gate");
        this.highFlow = highFlow;
        this.lowFlow = lowFlow;
    }

    @Override
    public void pushProxyFields() {
        super.pushProxyFields();

        machine.lua.pushJavaFunction(args -> {
            highFlow =  machine.lua.checkInteger(1);
            return 0;
        });
        machine.lua.setField(-2, "setSignalHighFlow");

        machine.lua.pushJavaFunction(args -> {
            lowFlow =  machine.lua.checkInteger(1);
            return 0;
        });
        machine.lua.setField(-2, "setSignalLowFlow");

        machine.lua.pushJavaFunction(args -> {
            machine.lua.pushInteger(lowFlow);
            return 1;
        });
        machine.lua.setField(-2, "getSignalLowFlow");

        machine.lua.pushJavaFunction(args -> {
            machine.lua.pushInteger(highFlow);
            return 1;
        });
        machine.lua.setField(-2, "getSignalHighFlow");

        machine.lua.pushJavaFunction(args -> {
            machine.lua.pushInteger(lowFlow);
            return 1;
        });
        machine.lua.setField(-2, "getFlow");

        machine.lua.pushJavaFunction(args -> {
            Override = machine.lua.checkBoolean(1);
            return 0;
        });
        machine.lua.setField(-2, "setOverrideEnabled");

        machine.lua.pushJavaFunction(args -> {
            machine.lua.pushBoolean(Override);
            return 1;
        });
        machine.lua.setField(-2, "getOverrideEnabled");
    }

    @Override
    public JSONObject toJSONObject() {
        return null;
    }

    public JSONObject toJSONObject(boolean b) {
        return new JSONObject()
                .put("type", type)
                .put("address", address)
                .put("highFlow", highFlow)
                .put("lowFlow", lowFlow);
    }
}
