package vm.computer.api;

import li.cil.repack.com.naef.jnlua.LuaState;
import vm.computer.LuaUtils;
import vm.computer.Machine;

public class Computer extends APIBase {
	public static final int
		energy = 85,
        maxEnergy = 100;
	
	public Computer(Machine machine) {
		super(machine, "computer");
	}

	@Override
	public void pushFields() {
		machine.lua.pushJavaFunction(args -> {
			for (String user : machine.users)
				machine.lua.pushString(user);
			
			return machine.users.size();
		});
		machine.lua.setField(-2, "users");
		
		machine.lua.pushJavaFunction(args -> {
			machine.users.add(args.checkString(1));
			machine.lua.pushBoolean(true);
			return 1;
		});
		machine.lua.setField(-2, "addUser");

		machine.lua.pushJavaFunction(args -> {
			String user = args.checkString(1);
			machine.users.remove(user);
			
			machine.lua.pushBoolean(true);
			return 1;
		});
		machine.lua.setField(-2, "removeUser");
		
		machine.lua.pushJavaFunction(args -> {
			boolean reboot = !args.isNoneOrNil(1) && args.checkBoolean(1);
			System.out.println("Shutdown computer.shutdown()");
			
			new Thread(() -> {
				machine.shutdown();
				if (reboot)
					machine.boot();
			}).start();
			
			return 0;
		});
		machine.lua.setField(-2, "shutdown");
		
		machine.lua.pushJavaFunction(args -> {
			machine.lua.newTable();
			return 1;
		});
		machine.lua.setField(-2, "getProgramLocations");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushString(machine.temporaryFilesystemComponent);

			return 1;
		});
		machine.lua.setField(-2, "tmpAddress");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushNumber((System.currentTimeMillis() - machine.startTime) / 1000d);
			return 1;
		});
		machine.lua.setField(-2, "uptime");

		machine.lua.pushJavaFunction(args -> {
			LuaState signal = new LuaState();
			LuaUtils.pushSignalData(signal, args, 1, args.getTop());
			machine.luaThread.pushSignal(signal);

			return 0;
		});
		machine.lua.setField(-2, "pushSignal");

		machine.lua.pushJavaFunction(args -> {
			LuaState signal = machine.luaThread.pullSignal(args.isNoneOrNil(1) ? Double.POSITIVE_INFINITY : args.checkNumber(1));

			return LuaUtils.pushSignalData(machine.lua, signal, 1, signal.getTop());
		});
		machine.lua.setField(-2, "pullSignal");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(machine.lua.getTotalMemory());
			return 1;
		});
		machine.lua.setField(-2, "totalMemory");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(machine.lua.getFreeMemory());
			return 1;
		});
		machine.lua.setField(-2, "freeMemory");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(false);
			return 1;
		});
		machine.lua.setField(-2, "isRobot");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(maxEnergy);
			return 1;
		});
		machine.lua.setField(-2, "maxEnergy");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(energy);
			return 1;
		});
		machine.lua.setField(-2, "energy");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.newTable();
			int tableIndex = machine.lua.getTop();
			
			machine.lua.pushNumber(1);
//			machine.lua.pushString("Lua 5.3");
//			machine.lua.pushNumber(2);
			machine.lua.pushString("Lua 5.2");
			
			machine.lua.setTable(tableIndex);
			
			return 1;
		});
		machine.lua.setField(-2,"getArchitectures");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushString("Lua 5.2");
			
			return 1;
		});
		machine.lua.setField(-2,"getArchitecture");

		LuaUtils.pushVoidFunction(machine.lua, "setArchitecture");
	}
}
