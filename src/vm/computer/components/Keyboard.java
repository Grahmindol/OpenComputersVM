package vm.computer.components;

import org.json.JSONObject;
import vm.computer.Machine;
import vm.computer.components.base.ComponentBase;

public class Keyboard extends ComponentBase {
    public Keyboard(Machine machine, String address, JSONObject obj) {
        super(machine, address, obj);
    }

    @Override
    public JSONObject toJSONObject(){
        return null;
    }
}
