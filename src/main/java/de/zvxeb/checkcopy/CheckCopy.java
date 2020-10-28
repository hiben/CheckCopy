package de.zvxeb.checkcopy;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import de.zvxeb.checkcopy.conflict.ChecksumConflict;
import de.zvxeb.checkcopy.conflict.SizeConflict;
import de.zvxeb.checkcopy.conflict.TypeConflict;
import de.zvxeb.checkcopy.exception.ChecksumException;
import de.zvxeb.checkcopy.exception.DirectoryReadException;
import de.zvxeb.checkcopy.exception.NotADirectoryException;
import de.zvxeb.checkcopy.gui.CheckCopyGUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Parameters(resourceBundle = "messages")
public class CheckCopy {
    private static Logger log = LoggerFactory.getLogger(CheckCopy.class);

    public static final String MD5 = "MD5";
    public static final String NULL = "null";

    @Parameter(order = 0, help = true, names = {"--help", "-h"}, descriptionKey = "cli_help")
    private boolean help = false;

    @Parameter(order = 1, names = {"--fail-fast", "-f"}, descriptionKey = "cli_fail_fast")
    public boolean failFast = false;

    @Parameter(order = 2, names = {"--fail-unexpected", "-u"}, descriptionKey = "cli_fail_unexpected")
    public boolean failUnexpected = false;

    @Parameter(order = 3, names = {"--no-size-check", "-s"}, descriptionKey = "cli_no_size_check")
    public boolean noSizeCheck = false;

    @Parameter(order = 4, names = {"--checksum", "-c"}, descriptionKey = "cli_checksum")
    public String checkSumAlgorithm = MD5;

    @Parameter(order = 5, names = {"--no-parallel-read", "-p"}, descriptionKey = "cli_no_parallel")
    public boolean noParallelRead = false;

    @Parameter(order = 6, names = {"--start-gui", "-g"}, descriptionKey = "cli_gui")
    public boolean startGui = false;

    @Parameter(order = 7, descriptionKey = "cli_paths")
    public List<String> sourceAndDestination;

    public static void main(String...args) throws IOException {
        ResourceBundle messages = ResourceBundle.getBundle("messages", Locale.getDefault());

        CheckCopy cc = new CheckCopy();
        JCommander jc= JCommander.
            newBuilder().
            addObject(cc).
            programName(messages.getString("title")).
            build();

        jc.parse(args);

        boolean startGui = cc.startGui || (args.length == 0 && System.console() == null);

        if(startGui) {
            log.info("Starting GUI");
            SwingUtilities.invokeLater(new CheckCopyGUI(cc));
            return;
        }

        if(cc.help) {
            jc.usage();
            return;
        }

        int files = cc.sourceAndDestination != null ? cc.sourceAndDestination.size() : 0;

        if(files != 2) {
            if(args.length==0) {
                jc.usage();
            } else {
                System.err.println(messages.getString("cli_error_no_paths"));
            }
            return;
        }

        if(cc.noSizeCheck) {
            System.out.println(messages.getString("process_no_size"));
            // check if this is the initial instance
            if(cc.checkSumAlgorithm == MD5) {
                // disable checksum for disabled size check
                cc.checkSumAlgorithm = NULL;
            }
        }

        MessageDigest md;
        if(cc.checkSumAlgorithm.equalsIgnoreCase(NULL)) {
            System.out.println(messages.getString("process_no_checksum"));
            md = null;
        } else {
            try {
                md = MessageDigest.getInstance(cc.checkSumAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
                System.err.println(String.format(messages.getString("error_digest_message"), cc.checkSumAlgorithm));
                System.err.println(String.format(messages.getString("error_java"), e.getLocalizedMessage()));
                return;
            }
        }

        if(cc.noSizeCheck && md!=null) {
            System.err.println(messages.getString("cli_error_size_checksum"));
            return;
        }

        if(cc.noParallelRead) {
            System.out.println(messages.getString("process_no_parallel_read"));
        }

        final CheckControl config =
            new CheckControl().
                checkSize(!cc.noSizeCheck).
                checksum(md).
                executor(cc.noParallelRead ? null : Executors.newFixedThreadPool(2))
                ;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.debug("Shutting down");
            config.cancel();
        }));
        CheckMeta meta = new CheckMeta();
        Path source = Paths.get(cc.sourceAndDestination.get(0));
        Path destination = Paths.get(cc.sourceAndDestination.get(1));
        Instant start = Instant.now();
        List<CheckResult> crl = checkCopy(config.init(), meta, source, destination);
        Instant end = Instant.now();
        Duration checkDuration = Duration.between(start, end);
        config.release();
        System.out.println(String.format(messages.getString("status_progress_final"), meta.numberOfFiles(), meta.numberOfDirectories(), formatTime(checkDuration)));
        System.out.println(String.format(messages.getString("cli_problems"), crl.size()));

        for(CheckResult cr : crl) {
            if(!cr.ok()) {
                System.out.format("%s (%s):\n", cr.sourcePath(), cr.destinationPath());
                if(!cr.notInDestination().isEmpty()) {
                    System.out.println(String.format(messages.getString("cli_not_in_destination"), cr.notInDestination().size()));
                    for(File f : cr.notInDestination()) {
                        System.out.format(" - %s%s\n", f.isDirectory() ? "[D] " : "", f.getName());
                    }
                }
                if(!cr.conflicts().isEmpty()) {
                    System.out.println(String.format(messages.getString("cli_conflict"), cr.conflicts().size()));
                    for(File f : cr.conflicts()) {
                        System.out.format(" - %s%s (%s)\n", f.isDirectory() ? "[D] " : "", f.getName(), cr.conflictDetails().get(f).getMessage());
                    }
                }
                if(!cr.notInSource().isEmpty()) {
                    System.out.println(String.format(messages.getString("cli_not_in_source"), cr.notInSource().size()));
                    for(File f : cr.notInSource()) {
                        System.out.format(" - %s%s\n", f.isDirectory() ? "[D] " : "", f.getName());
                    }
                }
            }
        }
    }

    public static List<CheckResult> checkCopy(CheckControl config, CheckMeta meta, Path source, Path destination) throws IOException {
        log.debug("Checking {} | {}", source, destination);
        if(config.cancelled()) {
            log.info("Operation cancelled...");
            return Collections.emptyList();
        }

        List<CheckResult> result = new LinkedList<>();
        File sourceDir = source.toFile();
        File destDir = destination.toFile();

        if(!sourceDir.isDirectory()) {
            throw new NotADirectoryException(true, sourceDir.getPath());
        }
        if(!destDir.isDirectory()) {
            throw new NotADirectoryException(false, destDir.getPath());
        }

        File [] sourceFiles = sourceDir.listFiles();
        File [] destinationFiles = destDir.listFiles();

        if(sourceFiles == null) {
            throw new DirectoryReadException(true, sourceDir.getPath());
        }
        if(destinationFiles == null) {
            throw new DirectoryReadException(false, destDir.getPath());
        }

        CheckResult cr = new CheckResult(source, destination);

        for(File fs : sourceFiles) {
            config.checkCancelled();

            String fsn = fs.getName();
            boolean fsd = fs.isDirectory();
            boolean found = false;
            boolean fail = false;
            for(File fd : destinationFiles) {
                config.checkCancelled();

                if(fsn.equals(fd.getName())) {
                    found = true;
                    if(fsd != fd.isDirectory()) {
                        TypeConflict tc = TypeConflict.causedBy(!fsd);
                        cr.addConflict(fs, tc);
                        if(config.eventListener()!=null) {
                            config.eventListener().onConflict(config, source, destination, fs, fd, tc);
                        }
                        fail = true;
                    } else {
                        if(!fsd && (config.checkSize() || config.checksum())) {
                            long sl = fs.length();
                            long dl = fd.length();

                            if(sl != dl) {
                                log.debug("{} - File sizes do not match - {} / {}", fsn, sl, dl);
                                found = false;
                                SizeConflict sc = SizeConflict.causedBy(sl, dl);
                                cr.addConflict(fs, sc);
                                if(config.eventListener()!=null) {
                                    config.eventListener().onConflict(config, source, destination, fs, fd, sc);
                                }
                            } else {
                                if (config.checksum()) {
                                    log.debug("Creating checksum for {}", fsn);
                                    byte [] scs = null;
                                    byte [] dcs = null;
                                    if(config.executor() != null) {
                                        Future<byte []> fscs =
                                            config.
                                                executor().
                                                    submit(
                                                        new ChecksumCallable(config, config.messageDigestS(), fs)
                                                    );
                                        Future<byte []> dscs =
                                            config.
                                                executor().
                                                    submit(
                                                        new ChecksumCallable(config, config.messageDigestD(), fd)
                                                    );
                                        try {
                                            scs = fscs.get();
                                        } catch (Exception e) {
                                            throw new ChecksumException(fs.getPath());
                                        }
                                        try {
                                            dcs = dscs.get();
                                        } catch (Exception e) {
                                            throw new ChecksumException(fd.getPath());
                                        }
                                    } else {
                                        scs = createChecksum(config, config.messageDigestS(), fs);
                                        dcs = createChecksum(config, config.messageDigestD(), fd);
                                    }
                                    config.checkCancelled();

                                    if (scs == null) {
                                        throw new ChecksumException(fs.getPath());
                                    }
                                    if (dcs == null) {
                                        throw new ChecksumException(fd.getPath());
                                    }

                                    String scss = digestToHex(scs);
                                    String dcss = digestToHex(dcs);

                                    if (!scss.equals(dcss)) {
                                        log.debug("Checksum mismatch found!");

                                        found = false;

                                        ChecksumConflict cc = ChecksumConflict.causedBy(scss, dcss);
                                        cr.addConflict(fs, cc);
                                        if(config.eventListener()!=null) {
                                            config.eventListener().onConflict(config, source, destination, fs, fd, cc);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
            if(meta != null) {
                if(fsd) {
                    meta.incDirectories();
                } else {
                    meta.incFiles();
                }
            }
            if(!found) {
                cr.addNotInDestination(fs);
                if(config.eventListener()!=null) {
                    config.eventListener().onNotInDestination(config, source, destination, fs);
                }
                fail = true;
            }
            if(fail && config.failFast()) {
                break;
            }
        }
        for(File fd : destinationFiles) {
            config.checkCancelled();

            String fdn = fd.getName();
            boolean found = false;
            boolean fail = false;
            for(File fs : sourceFiles) {
                if(fdn.equals(fs.getName())) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                cr.addNotInSource(fd);
                if(config.eventListener()!=null) {
                    config.eventListener().onNotInSource(config, source, destination, fd);
                }
                fail = true;
            }
            if(fail && config.failFast() && config.failOnDestination()) {
                break;
            }
        }

        if(!cr.ok()) {
            result.add(cr);
        }

        if(!config.failFast() || cr.ok()) {
            for (File fs : sourceFiles) {
                config.checkCancelled();
                if (fs.isDirectory() && !cr.notInDestination().contains(fs)) {
                    result.addAll(checkCopy(config, meta, source.resolve(fs.getName()), destination.resolve(fs.getName())));
                }
            }
        }

        return result;
    }

    public static byte [] createChecksum(CancellationCheck cc, MessageDigest md, File f) {
        md.reset();
        try(FileInputStream fis = new FileInputStream(f)) {
            byte [] buffer = new byte [64*1024];
            int r;
            while((r = fis.read(buffer)) > 0) {
                if(cc.cancelled()) {
                    log.debug("Checksum generation cancelled...");
                    return null;
                }
                md.update(buffer, 0, r);
            }
        } catch (IOException e) {
            log.error("Unable to perform digest for " + f.getName(), e);
            return null;
        }

        return md.digest();
    }

    public static String digestToHex(byte [] digest) {
        StringBuilder sb = new StringBuilder();
        for(byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String formatTime(Duration d) {
        long seconds = d.getSeconds();

        long minutes = seconds / 60;

        seconds %= 60;

        long hours = minutes / 60;

        minutes %= 60;

        if(hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            long milli = d.getNano() / (1000 * 1000);
            if(minutes > 0) {
                return String.format("%d:%02d.%d", minutes, seconds, milli / 100);
            } else {
                return String.format("%d.%03d", seconds, milli);
            }
        }
    }
}
