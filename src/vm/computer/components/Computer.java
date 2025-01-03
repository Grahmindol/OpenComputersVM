package vm.computer.components;

import vm.computer.Machine;
import vm.computer.components.base.ComponentBase;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Synthesizer;

import org.json.JSONObject;

public class Computer extends ComponentBase {
	private MidiChannel midiChannel;
	
	public Computer(Machine machine, String address,JSONObject obj) {
		super(machine, address, obj);

		try {
			Synthesizer synthesizer = MidiSystem.getSynthesizer();
			synthesizer.open();
			midiChannel = synthesizer.getChannels()[0];
			midiChannel.programChange(19);
		}
		catch (MidiUnavailableException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void pushProxyFields() {
		super.pushProxyFields();
		
		machine.lua.pushJavaFunction(args -> {
			rawBeep(args.checkInteger(1), (long) (args.checkNumber(2) * 1000));

			return 0;
		});
		machine.lua.setField(-2,  "beep");

//		machine.lua.pushJavaFunction(args -> {
//			machine.boot();
//
//			return 0;
//		});
//		machine.lua.setField(-2,  "start");
//
//		machine.lua.pushJavaFunction(args -> {
//			machine.shutdown(true);
//
//			return 0;
//		});
//		machine.lua.setField(-2,  "stop");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(true);

			return 1;
		});
		machine.lua.setField(-2,  "isRunning");
	}

	public void rawBeep(int frequency, long duration) {
		midiChannel.noteOn((int) (frequency / 2000d * 127), 127);
		
		try {
			Thread.sleep(duration);
		}
		catch (InterruptedException e) {}
		finally {
			midiChannel.allNotesOff();

			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {}
		}
	}
}
