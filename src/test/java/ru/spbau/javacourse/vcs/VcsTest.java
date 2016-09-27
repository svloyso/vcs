package ru.spbau.javacourse.vcs;

import org.junit.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import static org.junit.Assert.*;

public class VcsTest {

    static Path testDirectory = Paths.get("src/test/resources");
    static Path srcDirectory = Paths.get("src/test/resources/src");

    @Before
    public void init() throws Exception {
        Vcs.setCurrentDirectory(testDirectory);
        Vcs.init();
        Files.createFile(testDirectory.resolve("file.txt"));
        Files.createFile(testDirectory.resolve("Makefile.txt"));

        srcDirectory = Files.createDirectory(testDirectory.resolve("src"));
        Files.createFile(srcDirectory.resolve("code.cpp"));
        Files.createFile(srcDirectory.resolve("main.cpp"));
        Files.createFile(srcDirectory.resolve("lib.h"));
    }

    @Test
    public void testVcs() throws Exception {
        Path file = testDirectory.resolve("file.txt").toAbsolutePath();
        Vcs.add("file.txt");
        assertEquals(Collections.singleton(file.toString()), Vcs.getAdded());
        Vcs.commit("Message1");
        Thread.sleep(1000);
        Files.write(file, "lalala".getBytes());
        assertEquals(Collections.singleton(file.toString()), Vcs.getChanged());
        Vcs.commit("Message2");
        Thread.sleep(1000);

        Vcs.add("Makefile.txt");
        Vcs.add("src/code.cpp");
        Vcs.add("src/main.cpp");
        Vcs.commit("Message3");
        Thread.sleep(1000);

        Vcs.newBranch("test");
        Files.write(srcDirectory.resolve("code.cpp"), "11111".getBytes());
        Files.write(srcDirectory.resolve("main.cpp"), "22222".getBytes());
        Vcs.commit("Message4");
        Thread.sleep(1000);
        Vcs.checkOut("master");
        Thread.sleep(1000);
        assertEquals(0, Files.readAllBytes(srcDirectory.resolve("code.cpp")).length);
        assertEquals(0, Files.readAllBytes(srcDirectory.resolve("main.cpp")).length);
        Files.write(srcDirectory.resolve("code.cpp"), "33333".getBytes());
        Files.write(srcDirectory.resolve("main.cpp"), "44444".getBytes());
        Vcs.commit("Message5");
        Thread.sleep(1000);
        Vcs.merge("test", p -> p.equals(srcDirectory.resolve("code.cpp").toAbsolutePath()));
        assertArrayEquals("11111".getBytes(), Files.readAllBytes(srcDirectory.resolve("code.cpp")));
        assertArrayEquals("44444".getBytes(), Files.readAllBytes(srcDirectory.resolve("main.cpp")));
        Thread.sleep(1000);
        Files.write(testDirectory.resolve("file.txt"), "aaaa".getBytes());
        Vcs.reset("file.txt");
        assertArrayEquals("lalala".getBytes(), Files.readAllBytes(testDirectory.resolve("file.txt")));
        Files.createFile(testDirectory.resolve("111.txt"));
        Vcs.clean();
        assertFalse(Files.exists(testDirectory.resolve("111.txt")));
        assertTrue(Files.exists(testDirectory.resolve("file.txt")));
        assertTrue(Files.exists(srcDirectory.resolve("main.cpp")));
    }

    @After
    public void clean() throws Exception {
        Files.walkFileTree(testDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if(!dir.equals(testDirectory)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

}