package com.aloute.service.user;

import com.aloute.dto.post.PostView;
import com.aloute.model.user.Profile;
import org.springframework.stereotype.Component;

@Component("avatarFrameView")
public class AvatarFrameView {
    public String cssClass(Object avatar) {
        String frame = null;
        if (avatar instanceof Profile p) frame = p.getAvatarFrame();
        if (avatar instanceof PostView.AuthorView a) frame = a.avatarFrame();
        if (avatar instanceof com.aloute.dto.chat.MemberView m) frame = m.avatarFrame();
        return frame == null ? "" : "avatar-frame--" + frame;
    }
}
