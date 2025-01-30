package thesis_main_code.utils;

import thesis_main_code.protocols.NaiveStateTransfer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 * This class contains methods related to storage, such as reading from and writing to disk,
 * among any other related methods.
 */
public class DiskManagement {

    // With each EPOCH message, we generate some data with the specified size
    public static final int DATA_SIZE_MB = 100;
    public static final int MB_TO_BYTES = 1024 * 1024;
    public static final int EPOCH_DATA_SIZE = DATA_SIZE_MB * MB_TO_BYTES;

    // Every once in a while, we write to disk to simulate a (stable) checkpoint being made
    public static final int WRITE_THRESHOLD = 5;

    private String outputCheckpointFolderName;

    public DiskManagement(String outputCheckpointFolderName) {
        this.outputCheckpointFolderName = outputCheckpointFolderName;
    }

    /**
     * This function generates a certain-sized dummy byte array.
     * @return Dummy array of bytes.
     */
    public byte[] generateData(int epoch, boolean isByzantine) {
        // Simulating data
        byte[] data = new byte[EPOCH_DATA_SIZE];
        if (epoch % 4 == 0 && isByzantine) {
            Arrays.fill(data, (byte) 0);
        } else {
            Arrays.fill(data, (byte) 1);
        }
        return data;
    }

    /**
     * This function writes the data accumulated by this replica to the disk.
     * Used to simulate checkpoints during state transfer.
     * @param checkpoint The checkpoint which is written to disk
     * @param data The data to be written to disk
     * @throws IOException Throws an exception in case there is an error when writing to file
     */
    public void writeToDisk(int checkpoint, List<byte[]> data) throws IOException {

        String fileName = outputCheckpointFolderName + "/replica_checkpoint_" + (checkpoint + 1) + ".bin";
        try (FileOutputStream fos = new FileOutputStream(fileName)) {
            for (byte[] dataBlock : data) {
                fos.write(dataBlock);
//                NaiveStateTransfer.totalBytesWritten += dataBlock.length;
            }
//            System.out.println("Checkpoint written to disk to the following file : " + fileName);
        }

//        data.clear();
//        System.gc();
    }

    public byte[] readFromDisk(int checkpoint, int startIndex, int length) throws IOException {

        String fileName = outputCheckpointFolderName + "/replica_checkpoint_" + checkpoint + ".bin";
        File file = new File(fileName);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            // Seek to the desired offset
            raf.seek(startIndex);

            // Read the specified number of bytes
            byte[] data = new byte[length];
            int bytesRead = raf.read(data);

            if (bytesRead < length) {
                throw new IOException("Could not read the full requested length from file: " + bytesRead + " bytes read.");
            }

            return data;
        }
    }
}
