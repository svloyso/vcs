package ru.spbau.javacourse.vcs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by svloyso on 28.09.16.
 */
public class Main {
    public static void main(String[] args) throws IOException {
        Path res = Paths.get("src/test/resources").toAbsolutePath();
        Vcs.setCurrentDirectory(res);
        String line = "";
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while(!"exit".equals(line)) {
            System.out.print("> ");
            line = br.readLine();
            String command = line.substring(0, line.indexOf(' ') == -1 ? line.length() : line.indexOf(' '));
            String arg = line.length() == command.length() ? "" : line.substring(command.length() + 1);

            try {
                switch (command) {
                    case "init":
                        Vcs.init();
                        break;
                    case "status":
                        Set<String> added = Vcs.getAdded();
                        Set<String> changed = Vcs.getChanged();
                        Set<String> deleted = Vcs.getDeleted();
                        if (added.size() > 0) {
                            System.out.println("Added:");
                        }
                        added.stream().forEach(System.out::println);
                        if (changed.size() > 0) {
                            System.out.println("Changed:");
                        }
                        changed.stream().forEach(System.out::println);
                        if (deleted.size() > 0) {
                            System.out.println("Deleted:");
                        }
                        deleted.stream().forEach(System.out::println);
                        break;
                    case "add":
                        Vcs.add(arg);
                        break;
                    case "commit":
                        if (arg.isEmpty()) {
                            System.out.println("No commit message was given");
                            break;
                        }
                        Vcs.commit(arg);
                        break;
                    case "branches":
                        System.out.println(String.join(" ", Vcs.getBranches()));
                        break;
                    case "log":
                        System.out.println("Branch: " + Vcs.getBranch());
                        Vcs.getLog().stream().forEach(System.out::println);
                        break;
                    case "checkout":
                        Vcs.checkOut(arg);
                        break;
                    case "mkbranch":
                        Vcs.newBranch(arg);
                        break;
                    case "rmbranch":
                        Vcs.removeBranch(arg);
                        break;
                    case "reset":
                        Vcs.reset(arg);
                        break;
                    case "rm":
                        Vcs.remove(arg);
                        break;
                    case "clean":
                        Vcs.clean();
                        break;
                    case "merge":
                        Vcs.merge(arg, p -> {
                            System.out.println("Merge conflict in file " + p.toString() + ". Keep original file?(Y/N)");
                            String ans = "";
                            while (!ans.equals("Y") && !ans.equals("N")) {
                                try {
                                    ans = br.readLine();
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                            return !ans.equals("Y");
                        });
                        break;
                    default:
                        System.out.println("No such command");
                }
            } catch (RepoException e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }
}
