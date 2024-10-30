package vm.computer.components;

import org.json.JSONObject;
import vm.computer.Machine;

public class Keyboard extends ComponentBase {
    public Keyboard(Machine machine, String address) {
        super(machine, address, "keyboard");
    }

    @Override
    public JSONObject toJSONObject(){
        return null;
    }
}
