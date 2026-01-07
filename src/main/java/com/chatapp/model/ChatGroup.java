package com.chatapp.model;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatGroup {
    public String name;
    public String creator;
    public Set<String> members = ConcurrentHashMap.newKeySet();

    /**
     * Khởi tạo nhóm chat mới với tên nhóm và người tạo.
     * Người tạo sẽ tự động được thêm vào danh sách thành viên ngay lập tức.
     */
    public ChatGroup(String name, String creator) {
        this.name = name;
        this.creator = creator;
        this.members.add(creator);
    }

    /**
     * Khởi tạo nhóm chat chỉ với tên nhóm (người tạo mặc định là "UNKNOWN").
     */
    public ChatGroup(String name) {
        this(name, "UNKNOWN");
    }

    /**
     * Lấy danh sách (Set) các thành viên hiện đang có trong nhóm.
     */
    public Set<String> getMembers() {
        return members;
    }

    /**
     * Thêm một thành viên mới vào nhóm.
     * Trả về true nếu thêm thành công, false nếu người này đã có trong nhóm.
     */
    public boolean addMember(String username) {
        return members.add(username);
    }

    /**
     * Xóa một thành viên khỏi nhóm.
     * Trả về true nếu xóa thành công, false nếu người này không tồn tại trong nhóm.
     */
    public boolean removeMember(String username) {
        return members.remove(username);
    }

}