package io.jenkins.plugins.checks.steps;

import java.io.Serializable;

public class ChecksInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    public ChecksInfo(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
