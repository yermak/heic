package uk.yermak.audiobookconverter;

import net.bramp.ffmpeg.progress.ProgressParser;
import net.bramp.ffmpeg.progress.TcpProgressParser;
import uk.yermak.audiobookconverter.fx.ConverterApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Yermak on 29-Dec-17.
 */
public class FFMpegConverter implements Callable<ConverterOutput>, Converter {
    private final StatusChangeListener listener;
    private MediaInfo mediaInfo;
    private final String outputFileName;
    private ProgressCallback callback;
    private Process process;
    private final static String FFMPEG = new File("external/x64/ffmpeg.exe").getAbsolutePath();
    private ProgressParser progressParser = null;


    public FFMpegConverter(MediaInfo mediaInfo, String outputFileName, ProgressCallback callback) {
        this.mediaInfo = mediaInfo;
        this.outputFileName = outputFileName;
        this.callback = callback;
        listener = new StatusChangeListener();
        ConverterApplication.getContext().getConversion().addStatusChangeListener(listener);
    }

    public ConverterOutput convertMp3toM4a() throws IOException, InterruptedException {
        try {
            if (listener.isCancelled()) return null;
            while (listener.isCancelled()) Thread.sleep(1000);


            progressParser = new TcpProgressParser(progress -> {
                callback.converted(progress.out_time_ns / 1000000, progress.total_size);
                if (progress.isEnd()) {
                    callback.completedConversion();
                }
            });
            progressParser.start();
// ffmpeg -i 123.heic -qscale:v 2 -fmjpeg 123.jpg
            ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(FFMPEG,
                    "-i", mediaInfo.getFileName(),
                    "-vn",
                    "-codec:a", "libfdk_aac",
                    "-f", "ipod",
//                    "-b:a", String.valueOf(mediaInfo.getBitrate()),
                    "-ar", String.valueOf(mediaInfo.getFrequency()),
                    "-ac", String.valueOf(mediaInfo.getChannels()),
                    "-progress", progressParser.getUri().toString(),
                    outputFileName
            );

            process = ffmpegProcessBuilder.start();

            InputStream ffmpegIn = process.getInputStream();
            InputStream ffmpegErr = process.getErrorStream();

            StreamCopier.copy(ffmpegIn, System.out);
            StreamCopier.copy(ffmpegErr, System.err);

            boolean finished = false;
            while (!listener.isCancelled() && !finished) {
                finished = process.waitFor(500, TimeUnit.MILLISECONDS);
            }

            return new ConverterOutput(mediaInfo, outputFileName);
        } catch (CancellationException ce) {
            return null;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            Utils.closeSilently(process);
            Utils.closeSilently(progressParser);
            ConverterApplication.getContext().getConversion().removeStatusChangeListener(listener);
        }
    }

    @Override
    public ConverterOutput call() throws Exception {
        return convertMp3toM4a();
    }

}
