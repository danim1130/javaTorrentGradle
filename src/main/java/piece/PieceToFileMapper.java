package piece;

import torrent.torrentdata.FileData;
import util.Scheduler;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.function.Consumer;

public class PieceToFileMapper {

    private List<FileData> fileEntryList;

    public PieceToFileMapper(List<FileData> fileEntryList) {
        this.fileEntryList = fileEntryList;

        fileEntryList.forEach(it -> {
            File file = new File(it.getName());
            if (file.getParentFile() != null){
                try {
                    Files.createDirectories(file.getParentFile().toPath());
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void writePiece(byte[] data, long begin){
        long baseOffset = 0;
        int i = 0;
        while (baseOffset < begin + data.length){ //nullpointerexception
            FileData entry = fileEntryList.get(i);
            if (baseOffset + entry.getLength() > begin && baseOffset <= begin){ //Starting to write
                long offsetInFile = begin - baseOffset;
                ByteBuffer writeBuffer;
                if (entry.getLength() - offsetInFile >= data.length){
                    writeBuffer = ByteBuffer.wrap(data);
                } else {
                    writeBuffer = ByteBuffer.wrap(data, 0, (int) (entry.getLength() - offsetInFile));
                }
                try {
                    AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get(entry.getName()), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    AsyncWriteAttachment attachment = new AsyncWriteAttachment(writeBuffer, fileChannel, offsetInFile);

                    fileChannel.write(writeBuffer, offsetInFile, attachment, writeHandler);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (baseOffset > begin){
                int offsetInData = (int) (baseOffset - begin);
                int toWriteLength = (int) Math.min(entry.getLength(), data.length - offsetInData);
                ByteBuffer writeBuffer = ByteBuffer.wrap(data, offsetInData, offsetInData + toWriteLength);

                AsynchronousFileChannel fileChannel = null;
                try {
                    fileChannel = AsynchronousFileChannel.open(Paths.get(entry.getName()));
                    AsyncWriteAttachment attachment = new AsyncWriteAttachment(writeBuffer, fileChannel);
                    fileChannel.write(writeBuffer, 0, attachment, writeHandler);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            i++;
            baseOffset += entry.getLength();
        }
    }

    public void startReadBlock(long begin, int length, Consumer<byte[]> consumer){
        byte[] readBuffer = new byte[length];
        AsyncReadMaster master = new AsyncReadMaster(readBuffer, consumer);
        int readAttachmentCounter = 0;

        long baseOffset = 0;
        int i = 0;
        while (baseOffset < begin + length){
            FileData entry = fileEntryList.get(i);
            if (baseOffset + entry.getLength() > begin && baseOffset <= begin){ //Starting to write
                long offsetInFile = (begin - baseOffset);
                int readLength = (int) Math.min(entry.getLength() - offsetInFile, length);

                ByteBuffer fileReadBuffer = ByteBuffer.wrap(readBuffer, 0, readLength);
                try {
                    AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get(entry.getName()));
                    AsyncReadAttachment attachment = new AsyncReadAttachment(fileReadBuffer, fileChannel, master, offsetInFile);
                    readAttachmentCounter++;
                    fileChannel.read(fileReadBuffer, offsetInFile, attachment, readHandler);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (baseOffset > begin){
                int offsetInData = (int) (baseOffset - begin);
                int toReadLength = (int) Math.min(entry.getLength(), length - offsetInData);

                ByteBuffer fileReadBuffer = ByteBuffer.wrap(readBuffer, offsetInData, toReadLength);
                try {
                    AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get(entry.getName()));
                    AsyncReadAttachment attachment = new AsyncReadAttachment(fileReadBuffer, fileChannel, master, 0);
                    readAttachmentCounter++;
                    fileChannel.read(fileReadBuffer, 0, attachment, readHandler);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            i++;
            baseOffset += entry.getLength();
        }
        master.setNumberOfFiles(readAttachmentCounter);
    }

    private static final CompletionHandler<Integer, AsyncWriteAttachment> writeHandler = new CompletionHandler<Integer, AsyncWriteAttachment>() {
        @Override
        public void completed(Integer result, AsyncWriteAttachment attachment) {
            if (result == -1) {
                return;
            } else {
                attachment.increaseOffset(result);
            }

            if (attachment.getBuffer().hasRemaining()){
                attachment.getFileChannel().write(attachment.getBuffer(), attachment.getOffsetInFile(), attachment, writeHandler);
            }
        }

        @Override
        public void failed(Throwable exc, AsyncWriteAttachment attachment) {

        }
    };

    private static final CompletionHandler<Integer, AsyncReadAttachment> readHandler = new CompletionHandler<Integer, AsyncReadAttachment>() {
        @Override
        public void completed(Integer result, AsyncReadAttachment attachment) {
            if (result == -1) {
                return;
            } else {
                attachment.increaseOffset(result);
            }

            if (attachment.getReadBuffer().hasRemaining()){
                attachment.getFileChannel().read(attachment.getReadBuffer(), attachment.getOffsetInFile(), attachment, readHandler);
            } else {
                attachment.getReadMaster().fileReadCompleted();
            }
        }

        @Override
        public void failed(Throwable exc, AsyncReadAttachment attachment) {

        }
    };

}

 class AsyncWriteAttachment {
    private ByteBuffer buffer;
    private AsynchronousFileChannel fileChannel;
    private Long offsetInFile;

    public AsyncWriteAttachment(ByteBuffer buffer, AsynchronousFileChannel fileChannel) {
        this.buffer = buffer;
        this.fileChannel = fileChannel;
        this.offsetInFile = 0L;
    }

    public AsyncWriteAttachment(ByteBuffer buffer, AsynchronousFileChannel fileChannel, Long offsetInFile) {
        this.buffer = buffer;
        this.fileChannel = fileChannel;
        this.offsetInFile = offsetInFile;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public AsynchronousFileChannel getFileChannel() {
        return fileChannel;
    }

    public Long getOffsetInFile() {
        return offsetInFile;
    }

    public void increaseOffset(Integer increase){
        offsetInFile += increase;
    }
}

 class AsyncReadMaster {
    private byte[] globalBuffer;
    private Consumer<byte[]> consumer;
    private int completedCounter = 0;
    private int numberOfFiles = -1;

    public AsyncReadMaster(byte[] globalBuffer, Consumer<byte[]> consumer) {
        this.globalBuffer = globalBuffer;
        this.consumer = consumer;
    }

    public synchronized void setNumberOfFiles(int numberOfFiles) {
        this.numberOfFiles = numberOfFiles;
        if (numberOfFiles == completedCounter){
            completed();
        }
    }

    public synchronized void fileReadCompleted(){
        completedCounter++;
        if (numberOfFiles == completedCounter){
            completed();
        }
    }

    private void completed(){
        Scheduler.submit(() -> consumer.accept(globalBuffer));
    }
}

class AsyncReadAttachment {
    private ByteBuffer readBuffer;
    private AsynchronousFileChannel fileChannel;
    private AsyncReadMaster readMaster;
    private long offsetInFile;

    public AsyncReadAttachment(ByteBuffer readBuffer, AsynchronousFileChannel fileChannel, AsyncReadMaster readMaster, long baseOffset) {
        this.readBuffer = readBuffer;
        this.fileChannel = fileChannel;
        this.readMaster = readMaster;
        this.offsetInFile = baseOffset;
    }

    public ByteBuffer getReadBuffer() {
        return readBuffer;
    }

    public AsynchronousFileChannel getFileChannel() {
        return fileChannel;
    }

    public AsyncReadMaster getReadMaster() {
        return readMaster;
    }

    public long getOffsetInFile() {
        return offsetInFile;
    }

    public void increaseOffset(Integer increase){
        offsetInFile += increase;
    }
}
