package me.rexsystems.rexranks.importing;

import java.util.ArrayList;
import java.util.List;

public final class ImportResult {
    private int users;
    private int groups;
    private int tracks;
    private int nodes;
    private final List<String> warnings = new ArrayList<>();

    public void addUser() {
        users++;
    }

    public void addGroup() {
        groups++;
    }

    public void addTrack() {
        tracks++;
    }

    public void addNodes(int count) {
        nodes += count;
    }

    public void warn(String message) {
        warnings.add(message);
    }

    public int users() {
        return users;
    }

    public int groups() {
        return groups;
    }

    public int tracks() {
        return tracks;
    }

    public int nodes() {
        return nodes;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public String summary() {
        return "Imported " + groups + " groups, " + users + " users, " + tracks + " tracks (" + nodes + " nodes)"
                + (warnings.isEmpty() ? "" : " with " + warnings.size() + " warning(s)");
    }
}
