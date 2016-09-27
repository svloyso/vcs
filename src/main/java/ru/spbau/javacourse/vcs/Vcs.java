package ru.spbau.javacourse.vcs;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.sun.org.apache.regexp.internal.RE;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import org.apache.commons.codec.digest.DigestUtils;

public class Vcs {

    private static Path currentDirectory = Paths.get(".");
    private static Path repoDir = Paths.get("./.vcs");
    private static Path infoFile = Paths.get("./.vcs/info");

    static class RepoInfo implements Serializable{
        public Map<String, String> branches;
        public String latestBranch;
        public String latestHash;
        public Set<String> index;
    }

    static class CommitData implements Serializable {
        public static class CommitFile implements Serializable {
            String name;
            byte[] data;
            public CommitFile(String name, byte[] data) {
                this.name = name;
                this.data = data;
            }
        }
        public String hash;
        public String branch;
        public String parentHash;
        public FileTime time;
        public String message;
        public Set<String> index;
        public Set<String> changed;
        public Set<CommitFile> files;

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(hash);
            out.writeObject(branch);
            out.writeObject(parentHash);
            out.writeLong(time.toMillis());
            out.writeObject(message);
            out.writeObject(index);
            out.writeObject(changed);
            out.writeObject(files);
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            hash = (String)in.readObject();
            branch = (String)in.readObject();
            parentHash = (String)in.readObject();
            time = FileTime.fromMillis(in.readLong());
            message = (String)in.readObject();
            index = (Set<String>)in.readObject();
            changed = (Set<String>)in.readObject();
            files = (Set<CommitFile>)in.readObject();
        }
    }

    public static void setCurrentDirectory(Path dir) {
        currentDirectory = dir.toAbsolutePath();
        repoDir = currentDirectory.resolve(".vcs");
        infoFile = repoDir.resolve("info");
    }

    public static void init() throws RepoException {
        if(Files.exists(repoDir)) {
            throw new RepoException("Error: Repository in current directory already exists");
        }
        try {
            Files.createDirectory(repoDir);
        } catch (IOException e) {
            throw new RepoException("Can not create repo directory");
        }
        RepoInfo info = new RepoInfo();
        info.latestBranch = "master";
        info.latestHash = "";
        info.branches = new HashMap<>();
        info.index = new HashSet<>();
        writeInfo(info);
    }

    private static RepoInfo readInfo() throws RepoException {
        try {
            FileInputStream fis = new FileInputStream(infoFile.toFile());
            ObjectInputStream ois = new ObjectInputStream(fis);
            return (RepoInfo) ois.readObject();
        } catch (Exception e) {
            throw new RepoException("Can not read info file");
        }
    }

    private static void writeInfo(RepoInfo info) throws RepoException {
        info.branches.put(info.latestBranch, info.latestHash);
        try {
            FileOutputStream fos = new FileOutputStream(infoFile.toFile());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(info);
        } catch (Exception e) {
            throw new RepoException("Can not write info file");
        }
    }

    private static CommitData readCommit(String hash) throws RepoException {
        if(!Files.exists(repoDir.resolve(hash))) {
            throw new RepoException("Commit " + hash + " not found");
        }
        try {
            FileInputStream fos = new FileInputStream(repoDir.resolve(hash).toFile());
            ObjectInputStream oos = new ObjectInputStream(fos);
            return (CommitData) oos.readObject();
        } catch (Exception e) {
            throw new RepoException("Can not read commit " + hash);
        }
    }

    private static void writeCommit(CommitData commit) throws RepoException {
        try {
            FileOutputStream fos = new FileOutputStream(repoDir.resolve(commit.hash).toFile());
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(commit);
        } catch (Exception e) {
            throw new RepoException("Can not write commit");
        }
    }

    private static Set<String> getFilesUpdated(final FileTime from, final Set<String> index) throws RepoException {
        final HashSet<String> result = new HashSet<>();
        try {
            Files.walkFileTree(currentDirectory, new FileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if(dir.equals(repoDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(index.contains(file.toAbsolutePath().toString()) && attrs.lastModifiedTime().compareTo(from) > 0) {
                        result.add(file.toAbsolutePath().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RepoException("Can not traverse files in repo directory");
        }
        return result;
    }

    public static void add(String name) throws RepoException {
        Path path = currentDirectory.resolve(name);
        RepoInfo info = readInfo();
        if(info.index.contains(path.toString())) {
            throw new RepoException("File " + path + " already contains in index");
        }
        info.index.add(path.toString());
        writeInfo(info);
    }

    public static String getBranch() throws RepoException {
        RepoInfo info = readInfo();
        return info.latestBranch;
    }

    public static List<String> getBranches() throws RepoException {
        RepoInfo info = readInfo();
        return info.branches.keySet().stream().collect(Collectors.toList());
    }

    public static List<String> getLog() throws RepoException {
        RepoInfo info = readInfo();
        String hash = info.latestHash;
        List<String> res = new ArrayList<>();
        while(!hash.isEmpty()) {
            CommitData cd = readCommit(hash);
            res.add(cd.hash + ": " + cd.message);
            hash = cd.parentHash;
        }
        return res;
    }

    public static Set<String> getAdded() throws RepoException {
        RepoInfo info = readInfo();
        Set<String> res = info.index;
        if(!info.latestHash.isEmpty()) {
            CommitData cd = readCommit(info.latestHash);
            res.removeAll(cd.index);
        }
        return res;
    }

    public static Set<String> getChanged() throws RepoException {
        RepoInfo info = readInfo();
        if(info.latestHash.isEmpty()) {
            return Collections.emptySet();
        }
        CommitData cd = readCommit(info.latestHash);
        return getFilesUpdated(cd.time, cd.index);
    }

    public static Set<String> getDeleted() throws RepoException {
        RepoInfo info = readInfo();
        if(info.latestHash.isEmpty()) {
            return Collections.emptySet();
        }
        CommitData cd = readCommit(info.latestHash);
        return cd.index.stream().filter(p -> !Files.exists(Paths.get(p))).collect(Collectors.toSet());
    }

    public static void commit(String message) throws RepoException {
        RepoInfo info = readInfo();
        CommitData commit = new CommitData();
        commit.message = message;
        commit.time = FileTime.from(Instant.now());
        commit.branch = info.latestBranch;

        if(info.latestHash.isEmpty()) {
            commit.index = commit.changed = info.index;
            commit.parentHash = "";
        } else {
            CommitData prevCommit = readCommit(info.latestHash);
            Set<String> updatedFiles = getFilesUpdated(prevCommit.time, prevCommit.index);
            Set<String> deletedFiles = prevCommit.index.stream().filter(p -> !Files.exists(Paths.get(p))).collect(Collectors.toSet());

            commit.index = info.index;
            commit.index.removeAll(deletedFiles);
            commit.changed = new HashSet<>(commit.index);
            commit.changed.removeAll(prevCommit.index);
            commit.changed.addAll(updatedFiles);
            commit.parentHash = prevCommit.hash;
        }

        commit.files = commit.changed.stream().map(p -> {
            try {
                return new CommitData.CommitFile(p, Files.readAllBytes(Paths.get(p)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toSet());

        final ByteBuffer byteBuffer = ByteBuffer.allocate(
                commit.files.stream().mapToInt(b -> b.name.getBytes().length + b.data.length).sum());
        commit.files.stream().forEach(b -> {
            byteBuffer.put(b.name.getBytes());
            byteBuffer.put(b.data);
        });
        String hash = DigestUtils.md2Hex(byteBuffer.array());

        info.latestHash = hash;
        commit.hash = hash;

        writeCommit(commit);
        writeInfo(info);
    }

    public static void newBranch(String name) throws RepoException {
        RepoInfo info = readInfo();
        if(info.branches.containsKey(name)) {
            throw new RepoException("Branch " + name + " already exists");
        }
        info.latestBranch = name;
        writeInfo(info);
    }

    public static void removeBranch(String name) throws RepoException {
        RepoInfo info = readInfo();
        if(info.latestBranch.equals(name)) {
            throw new RepoException("Can not remove current branch. Checkout to other branch first.");
        }
        if(!info.branches.containsKey(name)) {
            throw new RepoException("Branch " + name + " not found");
        }
        info.branches.remove(name);
        writeInfo(info);
    }

    private static void clearDir(Path dir) throws RepoException {
        clearDir(dir, Collections.emptySet());
    }

    private static void clearDir(Path dir, Set<String> except) throws RepoException {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(!except.contains(file.toString())) {
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) throws IOException {
                    if(d.equals(repoDir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    if(!d.equals(dir) && Files.list(d).count() == 0) {
                        Files.delete(d);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RepoException("Can not clear repo directory");
        }
    }

    public static void checkOut(String arg) throws RepoException {
        RepoInfo info = readInfo();
        if (!info.branches.containsKey(arg)) {
            checkOutHash(arg);
            return;
        }
        String commitHash = info.branches.get(arg);
        checkOutHash(commitHash);
    }

    public static void checkOutHash(String hash) throws RepoException {
        CommitData cd = readCommit(hash);
        RepoInfo info = readInfo();
        info.latestBranch = cd.branch;
        info.latestHash = hash;
        info.index = cd.index;
        Map<String, Boolean> checkedFiles = cd.index.stream().collect(Collectors.toMap(p -> p, p -> false));
        clearDir(currentDirectory);

        while(checkedFiles.containsValue(false) && !hash.isEmpty()) {
            cd = readCommit(hash);
            cd.files.stream().forEach(f -> {
                if(checkedFiles.containsKey(f.name) && !checkedFiles.get(f.name)) {
                    checkedFiles.put(f.name, true);
                    ByteArrayInputStream bs = new ByteArrayInputStream(f.data);
                    try {
                        Files.createDirectories(Paths.get(f.name).getParent());
                        Files.copy(bs, Paths.get(f.name));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            hash = cd.parentHash;
        }
        writeInfo(info);
    }

    public static void merge(String arg, Predicate<Path> fileSelector) throws RepoException {
        RepoInfo info = readInfo();

        if (!info.branches.containsKey(arg)) {
            mergeHash(arg, fileSelector);
        } else {
            mergeHash(info.branches.get(arg), fileSelector);
        }
    }

    private static String findLCA(String hash1, String hash2) throws RepoException {
        CommitData cd1 = readCommit(hash1);
        CommitData cd2 = readCommit(hash2);
        while(!cd1.hash.equals(cd2.hash)) {
            if(cd1.time.compareTo(cd2.time) > 0) {
                cd1 = readCommit(cd1.parentHash);
            } else {
                cd2 = readCommit(cd2.parentHash);
            }
        }
        return cd1.hash;
    }

    public static void mergeHash(String hash, Predicate<Path> fileSelector) throws RepoException {
        RepoInfo info = readInfo();

        CommitData curCommitData = readCommit(info.latestHash);
        CommitData otherCommitData = readCommit(hash);

        Map<String, Boolean> checkedFiles = otherCommitData.index.stream().collect(Collectors.toMap(p -> p, p -> false));

        String lcaHash = findLCA(curCommitData.hash, otherCommitData.hash);

        while(!otherCommitData.hash.equals(lcaHash) && checkedFiles.containsValue(false)) {
            otherCommitData.files.stream().forEach(f -> {
                try {
                    checkedFiles.put(f.name, true);
                    Path name = Paths.get(f.name);
                    if(!Files.exists(name)) {
                        ByteArrayInputStream bs = new ByteArrayInputStream(f.data);
                        Files.createDirectories(name.getParent());
                        Files.copy(bs, name);
                        info.index.add(name.toString());
                    } else if(!Arrays.equals(f.data, Files.readAllBytes(name))) {
                        if(fileSelector.test(name)) {
                            Files.write(name, f.data);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            otherCommitData = readCommit(otherCommitData.parentHash);
        }
        writeInfo(info);
        commit("Merge with revision " + hash + " of branch " + curCommitData.branch);
    }

    public static void reset(String name) throws RepoException {
        Path path = currentDirectory.resolve(name).toAbsolutePath();
        RepoInfo info = readInfo();
        String hash = info.latestHash;
        while(!hash.isEmpty()) {
            CommitData cd = readCommit(hash);
            boolean found = false;
            for(CommitData.CommitFile f : cd.files) {
                if(f.name.equals(path.toString())) {
                    try {
                        if(!Files.exists(path)) {
                            ByteArrayInputStream bs = new ByteArrayInputStream(f.data);
                            Files.createDirectories(path.getParent());
                            Files.copy(bs, path);
                        } else {
                            Files.write(path, f.data);
                        }
                    } catch (IOException e) {
                        throw new RepoException("Can not reset file " + path.toString());
                    }
                    found = true;
                    break;
                }
            }
            if(found) {
                break;
            }
            hash = cd.parentHash;
        }
    }

    public static void remove(String file) throws RepoException {
        Path path = currentDirectory.resolve(file).toAbsolutePath();
        RepoInfo info = readInfo();
        if(!info.index.contains(path.toString())) {
            throw new RepoException("File " + file + " not in index");
        }
        info.index.remove(path.toString());
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new RepoException("Can not remove file " + file);
        }
        writeInfo(info);
    }

    public static void clean() throws RepoException {
        final RepoInfo info = readInfo();
        clearDir(currentDirectory, info.index);
    }
}
