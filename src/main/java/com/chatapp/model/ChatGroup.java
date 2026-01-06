package com.chatapp.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatGroup {
    public String name;
    public String creator;
    public Set<String> members = ConcurrentHashMap.newKeySet();

    public ChatGroup(String name, String creator) {
        this.name = name;
        this.creator = creator;
        this.members.add(creator);
    }

    public ChatGroup(String name) {
        this(name, "UNKNOWN");
    }

    public Set<String> getMembers() {
        return members;
    }

    public boolean addMember(String username) {
        return members.add(username);
    }

    public boolean removeMember(String username) {
        return members.remove(username);
    }

}
