package com.aloute.sticker;

import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.exception.chat.ChatActionException;
import com.aloute.model.sticker.StickerPack;
import com.aloute.model.user.Role;
import com.aloute.model.user.User;
import com.aloute.service.chat.ChatService;
import com.aloute.service.chat.MessageService;
import com.aloute.service.sticker.LocalStickerLibrary;
import com.aloute.service.sticker.StickerAdminService;
import com.aloute.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StickerAdminIT extends IntegrationTest {
    @Autowired StickerAdminService admin;
    @Autowired LocalStickerLibrary library;
    @Autowired MessageService messages;
    @Autowired ChatService chats;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private final List<String> created=new ArrayList<>();
    private String create(User actor,StickerPack.AccessType type) {
        String id=admin.create(actor.getId(),"Bộ thử nghiệm","Mô tả",type);created.add(id);return id;
    }
    private MockMultipartFile png() throws Exception {
        BufferedImage image=new BufferedImage(40,24,BufferedImage.TYPE_INT_ARGB);image.setRGB(10,10,0xffff0000);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        return new MockMultipartFile("files","hello.png","image/png",out.toByteArray());
    }
    private String upload(User actor,String id) throws Exception {
        admin.upload(actor.getId(),id,List.of(png()),List.of("Ăn mừng"));return admin.get(actor.getId(),id).stickers().get(0).id();
    }
    @AfterEach void retireFreeFixtures() { for(String id:created)jdbc.update("update sticker_packs set status='RETIRED' where id=? and status='PUBLISHED'",id); }

    @Test void awardCanBeGrantedRevokedAndRegrantedWithoutBreakingHistory() throws Exception {
        User a=createUser(Role.ADMIN),owner=createUser(),other=createUser();
        String id=create(a,StickerPack.AccessType.AWARD),sticker=upload(a,id);
        var conversation=chats.startDirect(owner.getId(),other.getId());
        assertThat(library.library(owner.getId()).packs()).noneMatch(p->p.id().equals(id));
        admin.publish(a.getId(),id);
        assertThatThrownBy(()->messages.sendSticker(owner.getId(),conversation.getId(),sticker,null)).isInstanceOf(ChatActionException.class);
        assertThatThrownBy(()->library.favorite(owner.getId(),sticker,true)).isInstanceOf(ChatActionException.class);
        assertThat(admin.grant(a.getId(),id,List.of(owner.getId()),"Giải nhất")).isEqualTo(1);
        assertThat(admin.grant(a.getId(),id,List.of(owner.getId()),"Cấp lặp")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where recipient_id=? and type='STICKER_REWARD'",Integer.class,owner.getId())).isEqualTo(1);
        var pack=library.ownedPack(owner.getId(),id);
        assertThat(pack.accessType()).isEqualTo("AWARD");assertThat(pack.stickers().get(0).renderType()).isEqualTo("IMAGE");assertThat(pack.stickers().get(0).artwork()).isNull();
        library.favorite(owner.getId(),sticker,true);
        var sent=messages.sendSticker(owner.getId(),conversation.getId(),sticker,null);
        mvc.perform(get("/stickers/"+id).with(asUser(owner))).andExpect(status().isOk()).andExpect(content().string(containsString("sticker-image")));
        mvc.perform(get("/stickers/"+id).with(asUser(other))).andExpect(status().isNotFound());
        mvc.perform(get("/notifications").with(asUser(owner))).andExpect(status().isOk()).andExpect(content().string(containsString("/stickers/"+id)));
        admin.revoke(a.getId(),id,owner.getId());
        var after=library.library(owner.getId());assertThat(after.packs()).noneMatch(p->p.id().equals(id));assertThat(after.recent()).doesNotContain(sticker);assertThat(after.favorites()).doesNotContain(sticker);
        assertThatThrownBy(()->messages.sendSticker(owner.getId(),conversation.getId(),sticker,null)).isInstanceOf(ChatActionException.class);
        assertThat(messages.history(other.getId(),conversation.getId(),null).messages()).anySatisfy(m->{assertThat(m.id()).isEqualTo(sent.id());assertThat(m.sticker().imageUrl()).isEqualTo(sent.sticker().imageUrl());});
        mvc.perform(get("/messages/"+conversation.getId()).with(asUser(owner))).andExpect(status().isOk()).andExpect(content().string(containsString(sent.sticker().imageUrl())));
        assertThat(admin.grant(a.getId(),id,List.of(owner.getId()),"Cấp lại")).isEqualTo(1);
        assertThat(library.ownedPack(owner.getId(),id).id()).isEqualTo(id);
    }

    @Test void retiredFreePackKeepsExistingOwnersAndRejectsNewUsers() throws Exception {
        User a=createUser(Role.ADMIN),owner=createUser(),newUser=createUser();
        String id=create(a,StickerPack.AccessType.FREE),sticker=upload(a,id);admin.publish(a.getId(),id);
        assertThat(library.library(owner.getId()).packs()).anyMatch(p->p.id().equals(id));
        admin.retire(a.getId(),id);
        assertThat(library.library(owner.getId()).packs()).anyMatch(p->p.id().equals(id));
        assertThat(library.library(newUser.getId()).packs()).noneMatch(p->p.id().equals(id));
        var c=chats.startDirect(owner.getId(),newUser.getId());
        messages.sendSticker(owner.getId(),c.getId(),sticker,null);
        assertThatThrownBy(()->messages.sendSticker(newUser.getId(),c.getId(),sticker,null)).isInstanceOf(ChatActionException.class);
        admin.publish(a.getId(),id);messages.sendSticker(newUser.getId(),c.getId(),sticker,null);
    }

    @Test void publishedImagesAreImmutableButMetadataAndAdditionsWork() throws Exception {
        User a=createUser(Role.ADMIN);String id=create(a,StickerPack.AccessType.AWARD);
        assertThatThrownBy(()->admin.publish(a.getId(),id)).isInstanceOf(InvalidAdminActionException.class);
        String sticker=upload(a,id);admin.updateSticker(a.getId(),id,sticker,"Tên mới","vui, chien thang",8,png());
        admin.publish(a.getId(),id);String url=admin.get(a.getId(),id).stickers().get(0).imageUrl();
        assertThatThrownBy(()->admin.updateSticker(a.getId(),id,sticker,"Tên mới","",0,png())).isInstanceOf(InvalidAdminActionException.class);
        assertThatThrownBy(()->admin.save(a.getId(),id,"Tên","",StickerPack.AccessType.FREE,null)).isInstanceOf(InvalidAdminActionException.class);
        admin.updateSticker(a.getId(),id,sticker,"Đã đổi tên","vui",3,null);admin.upload(a.getId(),id,List.of(png()),List.of("Ảnh thêm"));
        assertThat(admin.get(a.getId(),id).stickers()).hasSize(2).anySatisfy(s->{assertThat(s.id()).isEqualTo(sticker);assertThat(s.imageUrl()).isEqualTo(url);assertThat(s.keywords()).isEqualTo("vui");});
    }

    @Test void removingPublishedStickerPreservesOldMessagesButPreventsNewSends() throws Exception {
        User adminUser=createUser(Role.ADMIN), sender=createUser(), other=createUser();
        String id=create(adminUser,StickerPack.AccessType.FREE), sticker=upload(adminUser,id);
        admin.publish(adminUser.getId(),id);
        var conversation=chats.startDirect(sender.getId(),other.getId());
        library.favorite(sender.getId(),sticker,true);
        var sent=messages.sendSticker(sender.getId(),conversation.getId(),sticker,null);
        admin.deleteSticker(adminUser.getId(),id,sticker);

        assertThat(admin.get(adminUser.getId(),id).stickers()).isEmpty();
        assertThat(admin.get(adminUser.getId(),id).totalStickers()).isZero();
        assertThat(library.library(sender.getId()).favorites()).doesNotContain(sticker);
        assertThatThrownBy(()->messages.sendSticker(sender.getId(),conversation.getId(),sticker,null)).isInstanceOf(ChatActionException.class);
        assertThat(messages.history(other.getId(),conversation.getId(),null).messages()).anySatisfy(m->{
            assertThat(m.id()).isEqualTo(sent.id());
            assertThat(m.sticker().imageUrl()).isEqualTo(sent.sticker().imageUrl());
        });
    }

    @Test void invalidBatchGrantsNobodyAndConcurrentGrantsAreIdempotent() throws Exception {
        User a=createUser(Role.ADMIN),owner=createUser();String id=create(a,StickerPack.AccessType.AWARD);upload(a,id);admin.publish(a.getId(),id);
        assertThatThrownBy(()->admin.grant(a.getId(),id,List.of(owner.getId(),UUID.randomUUID()),"Giải thưởng")).isInstanceOf(InvalidAdminActionException.class);
        assertThat(jdbc.queryForObject("select count(*) from user_sticker_packs where pack_id=?",Integer.class,id)).isZero();
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task=()->{start.await();return admin.grant(a.getId(),id,List.of(owner.getId()),"Giải thưởng");};
            var x=executor.submit(task);var y=executor.submit(task);start.countDown();assertThat(List.of(x.get(),y.get())).containsExactlyInAnyOrder(1,0);
        } finally {executor.shutdownNow();}
        assertThat(jdbc.queryForObject("select count(*) from notifications where sticker_pack_id=?",Integer.class,id)).isEqualTo(1);
        admin.retire(a.getId(),id);assertThatThrownBy(()->admin.grant(a.getId(),id,List.of(owner.getId()),"Giải mới")).isInstanceOf(InvalidAdminActionException.class);
    }

    @Test void adminScreensAreProtectedAndRenderEmptyDraftAndRecipientManagement() throws Exception {
        User a=createUser(Role.ADMIN),manager=createUser(Role.MANAGER),normal=createUser();String id=create(a,StickerPack.AccessType.AWARD);
        mvc.perform(get("/admin/stickers").with(asUser(manager))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/stickers").with(asUser(normal))).andExpect(status().isForbidden());
        assertThatThrownBy(()->admin.create(manager.getId(),"Hack","",StickerPack.AccessType.FREE)).isInstanceOf(AccessDeniedException.class);
        var listHtml=mvc.perform(get("/admin/stickers").with(asUser(a))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(get("/admin/stickers/"+id).with(asUser(a))).andExpect(status().isOk()).andExpect(content().string(containsString("Thêm ảnh sticker")));
        upload(a,id);admin.publish(a.getId(),id);
        var editHtml=mvc.perform(get("/admin/stickers/"+id).with(asUser(a))).andExpect(status().isOk()).andExpect(content().string(containsString("sticker-grant-form"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Path preview=Path.of("target/sticker-admin-check/previews");Files.createDirectories(preview);Files.writeString(preview.resolve("list.html"),listHtml);Files.writeString(preview.resolve("edit.html"),editHtml);
        mvc.perform(get("/admin/stickers/recipients").param("q",normal.getUsername()).with(asUser(a))).andExpect(status().isOk()).andExpect(jsonPath("$[0].username").value(normal.getUsername()));
    }

    private long fileCount() throws Exception {
        Path p=Path.of("target/test-uploads/stickers");if(!Files.exists(p))return 0;try(var files=Files.list(p)){return files.count();}
    }
    @Test void uploadsCleanUpOnRollbackAndRejectInvalidBatchesBeforeSaving() throws Exception {
        User a=createUser(Role.ADMIN);String id=create(a,StickerPack.AccessType.FREE);long before=fileCount();
        var bad=new MockMultipartFile("files","bad.png","image/png",new byte[]{1,2,3});
        assertThatThrownBy(()->admin.upload(a.getId(),id,List.of(png(),bad),List.of("A","B"))).isInstanceOf(InvalidAdminActionException.class);
        assertThat(fileCount()).isEqualTo(before);
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(s->{try{admin.upload(a.getId(),id,List.of(png()),List.of("A"));}catch(Exception e){throw new RuntimeException(e);}throw new IllegalStateException("Rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(fileCount()).isEqualTo(before);assertThat(admin.get(a.getId(),id).stickers()).isEmpty();
        upload(a,id);String sticker=admin.get(a.getId(),id).stickers().get(0).id();admin.deleteSticker(a.getId(),id,sticker);assertThat(admin.get(a.getId(),id).totalStickers()).isZero();
    }

    @Test void enforcesTenImagesPerBatchAndFiftyPerPack() throws Exception {
        User a=createUser(Role.ADMIN);String id=create(a,StickerPack.AccessType.AWARD);var file=png();
        assertThatThrownBy(()->admin.upload(a.getId(),id,Collections.nCopies(11,file),Collections.nCopies(11,"Ảnh"))).isInstanceOf(InvalidAdminActionException.class);
        for(int i=0;i<5;i++)admin.upload(a.getId(),id,Collections.nCopies(10,file),Collections.nCopies(10,"Ảnh"));
        assertThat(admin.get(a.getId(),id).totalStickers()).isEqualTo(50);
        assertThatThrownBy(()->admin.upload(a.getId(),id,List.of(file),List.of("Thêm"))).isInstanceOf(InvalidAdminActionException.class);
        assertThat(admin.get(a.getId(),id).stickers()).hasSize(50);
    }
}
