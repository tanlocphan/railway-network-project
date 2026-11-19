package com.railway.setup.core;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Read-only questions to git, for the Git page header and pickers. */
public final class Git {

    public static final class Info {
        public boolean available;
        public boolean repository;
        public String branch = "";
        public String head = "";
        public String subject = "";
        public String upstream;
        public int ahead;
        public int behind;
        public List<String> changes = new ArrayList<>();
        public List<String> localBranches = new ArrayList<>();
        public List<String> remoteBranches = new ArrayList<>();
        public String remoteUrl;
        public boolean merging;

        public String webUrl() {
            if (remoteUrl == null) {
                return null;
            }
            String url = remoteUrl.trim();
            if (url.startsWith("git@")) {
                url = "https://" + url.substring(4).replaceFirst(":", "/");
            }
            if (url.endsWith(".git")) {
                url = url.substring(0, url.length() - 4);
            }
            return url.startsWith("http") ? url : null;
        }
    }

    private Git() {
    }

    public static Info read() {
        Info info = new Info();
        info.available = run("--version") != null;
        if (!info.available) {
            return info;
        }
        String top = run("rev-parse", "--show-toplevel");
        info.repository = top != null;
        if (!info.repository) {
            return info;
        }
        info.branch = orEmpty(run("rev-parse", "--abbrev-ref", "HEAD")).trim();
        String log = run("log", "-1", "--format=%h%x09%s");
        if (log != null && log.contains("\t")) {
            info.head = log.substring(0, log.indexOf('\t'));
            info.subject = log.substring(log.indexOf('\t') + 1);
        }
        info.upstream = run("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}");
        if (info.upstream != null) {
            String counts = run("rev-list", "--left-right", "--count", "HEAD...@{u}");
            if (counts != null) {
                String[] parts = counts.trim().split("\\s+");
                if (parts.length == 2) {
                    info.ahead = parse(parts[0]);
                    info.behind = parse(parts[1]);
                }
            }
        }
        String status = run("status", "--porcelain");
        if (status != null && !status.isBlank()) {
            info.changes.addAll(List.of(status.split("\\R")));
        }
        info.localBranches.addAll(lines(run("branch", "--format=%(refname:short)")));
        for (String r : lines(run("branch", "-r", "--format=%(refname:short)"))) {
            if (!r.endsWith("/HEAD") && r.contains("/")) {
                info.remoteBranches.add(r);
            }
        }
        String remote = run("remote", "get-url", "origin");
        info.remoteUrl = remote == null ? null : remote.trim();
        if (info.upstream != null) {
            info.upstream = info.upstream.trim();
        }
        info.merging = Files.exists(Project.root().resolve(".git").resolve("MERGE_HEAD"));
        return info;
    }

    /** Is this a name git accepts for a new branch? */
    public static boolean validBranchName(String name) {
        return name != null && !name.isBlank() && run("check-ref-format", "--branch", name) != null;
    }

    private static String run(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Proc.Result r = Proc.capture(command, Project.root(), 10);
        return r.ok() ? r.output() : null;
    }

    private static List<String> lines(String text) {
        List<String> out = new ArrayList<>();
        if (text != null) {
            for (String l : text.split("\\R")) {
                if (!l.isBlank()) {
                    out.add(l.trim());
                }
            }
        }
        return out;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
