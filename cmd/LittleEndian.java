package cmd;
//Little Endian class by ViveTheModder
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LittleEndian {
	public static byte[] getByteArrayFromInt(int data) {
		ByteBuffer bb = ByteBuffer.allocate(4);
		if (!Main.wiiMode)
			bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.asIntBuffer().put(data);
		return bb.array();
	}
	public static byte[] getByteArrayFromShort(short data) {
		ByteBuffer bb = ByteBuffer.allocate(2);
		if (!Main.wiiMode)
			bb.order(ByteOrder.LITTLE_ENDIAN);
		bb.asShortBuffer().put(data);
		return bb.array();
	}
	public static int getInt(int data) {
		if (Main.wiiMode)
			return data;
		ByteBuffer bb = ByteBuffer.allocate(4);
		bb.asIntBuffer().put(data);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getInt();
	}
	public static short getShort(short data) {
		if (Main.wiiMode)
			return data;
		ByteBuffer bb = ByteBuffer.allocate(2);
		bb.asShortBuffer().put(data);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getShort();
	}
	public static void swapByteArrayOrder(byte[] array, boolean isInt) {
		if (isInt) {
			//swap the 1st and 4th byte via XOR
			array[0] ^= array[3];
			array[3] ^= array[0];
			array[0] ^= array[3];
			//swap the 2nd and 3rd byte via XOR
			array[1] ^= array[2];
			array[2] ^= array[1];
			array[1] ^= array[2];
		} else {
			//swap the 1st and 2nd byte via XOR
			array[0] ^= array[1];
			array[1] ^= array[0];
			array[0] ^= array[1];
		}
	}
}