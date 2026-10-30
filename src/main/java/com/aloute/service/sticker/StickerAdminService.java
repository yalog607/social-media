package com.aloute.service.sticker;

import com.aloute.dto.admin.StaffMember;
import com.aloute.dto.sticker.StickerPackView;
import com.aloute.exception.admin.InvalidAdminActionException;
import com.aloute.model.sticker.Sticker;
import com.aloute.model.sticker.StickerPack;
import com.aloute.model.user.Role;
import com.aloute.repository.sticker.StickerPackRepository;
import com.aloute.repository.sticker.StickerRepository;
import com.aloute.repository.user.UserRepository;
import com.aloute.service.admin.AdminService;
import com.aloute.service.audit.AuditService;
import com.aloute.service.notification.NotificationService;
import com.aloute.service.storage.StorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class StickerAdminService {
    private final StickerPackRepository packs;
    private final StickerRepository stickers;
    private final UserRepository users;
    private final StickerService views;
    private final StickerAccess access;
    private final StorageService storage;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AuditService audit;
    private final NotificationService notifications;
    private final AdminService admin;
    public StickerAdminService(StickerPackRepository packs, StickerRepository stickers, UserRepository users,
                               StickerService views, StickerAccess access, StorageService storage, JdbcTemplate jdbc,
                               Clock clock, AuditService audit, NotificationService notifications, AdminService admin) {
        this.packs=packs; this.stickers=stickers; this.users=users; this.views=views; this.access=access;
        this.storage=storage; this.jdbc=jdbc; this.clock=clock; this.audit=audit; this.notifications=notifications; this.admin=admin;
    }
    private void requireAdmin(UUID id) {
        if (!users.findById(id).filter(u -> u.isActive() && u.hasRole(Role.ADMIN)).isPresent()) throw new AccessDeniedException("Chỉ Admin được quản lý nhãn dán.");
    }
    private StickerPack locked(String id) {
        access.lock(id);
        return packs.findById(id).orElseThrow(() -> invalid("Không tìm thấy bộ nhãn dán."));
    }
    private static InvalidAdminActionException invalid(String text) { return new InvalidAdminActionException(text); }
    private static String text(String value, int max, boolean required, String label) {
        String normalized=value == null ? "" : value.strip();
        if ((required && normalized.isEmpty()) || normalized.length()>max) throw invalid(label + (required ? " không được trống và" : "") + " tối đa " + max + " ký tự.");
        return normalized;
    }
    private void log(UUID actor, String action, String pack, String detail) { audit.log(actor, action, "STICKER_PACK", null, pack + " · " + detail); }

    @Transactional(readOnly=true)
    public List<StickerPackView> list(UUID actor) {
        requireAdmin(actor);
        return packs.findAllByOrderBySortOrderAsc().stream()
                .filter(p -> stickers.findByPackIdOrderBySortOrderAsc(p.getId()).stream().noneMatch(s -> s.getRenderType()==Sticker.RenderType.LEGACY))
                .map(p -> views.toPackView(p,false,true)).toList();
    }
    @Transactional(readOnly=true)
    public StickerPackView get(UUID actor, String id) {
        requireAdmin(actor);
        return views.toPackView(packs.findById(id).orElseThrow(() -> invalid("Không tìm thấy bộ nhãn dán.")),false,true);
    }
    @Transactional(readOnly=true)
    public boolean editableImages(UUID actor, String id) {
        requireAdmin(actor); return !packs.findById(id).orElseThrow().isEverPublished();
    }

    @Transactional
    public String create(UUID actor, String name, String description, StickerPack.AccessType type) {
        requireAdmin(actor);
        StickerPack p=new StickerPack(); p.setId(UUID.randomUUID().toString());
        p.setName(text(name,100,true,"Tên bộ")); p.setDescription(text(description,255,false,"Mô tả"));
        p.setAccessType(Objects.requireNonNull(type)); p.setCreatedAt(clock.instant()); p.setIconUrl("/logo-proposals/aloute-sticker-icon.svg");
        packs.saveAndFlush(p); log(actor,"STICKER_PACK_CREATED",p.getId(),p.getName()); return p.getId();
    }

    @Transactional
    public void save(UUID actor,String id,String name,String description,StickerPack.AccessType type,MultipartFile icon) {
        requireAdmin(actor); StickerPack p=locked(id);
        String newName=text(name,100,true,"Tên bộ"), newDescription=text(description,255,false,"Mô tả");
        if (p.isEverPublished() && p.getAccessType()!=type) throw invalid("Không đổi loại bộ sau khi đã xuất bản. Hãy tạo bộ mới.");
        if (icon!=null && !icon.isEmpty()) {
            var image=StickerImageValidator.validate(icon); String old=p.getIconUrl();
            p.setIconUrl(store(image));
            if (!stickers.existsByImageUrl(old)) deleteAfterCommit(old);
        }
        p.setName(newName); p.setDescription(newDescription); p.setAccessType(type);
        packs.saveAndFlush(p); log(actor,"STICKER_PACK_UPDATED",id,newName);
    }

    @Transactional
    public void upload(UUID actor,String id,List<MultipartFile> files,List<String> names) {
        requireAdmin(actor); StickerPack p=locked(id);
        if (files==null || files.isEmpty() || files.size()>10 || files.stream().anyMatch(f -> f==null || f.isEmpty())) throw invalid("Chọn từ 1 đến 10 ảnh mỗi lần tải.");
        var current=stickers.findByPackIdOrderBySortOrderAsc(id);
        if (current.size()+files.size()>50) throw invalid("Mỗi bộ tối đa 50 nhãn dán.");
        if (names!=null && !names.isEmpty() && names.size()!=files.size()) throw invalid("Mỗi ảnh cần một tên tương ứng.");
        List<String> labels=new ArrayList<>();
        List<StickerImageValidator.Image> images=new ArrayList<>();
        for(int i=0;i<files.size();i++) {
            String defaultName=Objects.toString(files.get(i).getOriginalFilename(),"Nhãn dán").replaceFirst("\\.[^.]+$", "");
            labels.add(text(names!=null && !names.isEmpty()?names.get(i):defaultName,100,true,"Tên sticker"));
            images.add(StickerImageValidator.validate(files.get(i)));
        }
        int order=current.stream().mapToInt(Sticker::getSortOrder).max().orElse(-1)+1;
        for(int i=0;i<images.size();i++) {
            Sticker s=new Sticker();s.setId(UUID.randomUUID().toString());s.setPack(p);s.setCode(UUID.randomUUID().toString());
            s.setName(labels.get(i));s.setImageUrl(store(images.get(i)));s.setRenderType(Sticker.RenderType.IMAGE);s.setSortOrder(order++);
            stickers.save(s);
            if (current.isEmpty() && i==0 && p.getIconUrl().startsWith("/logo-proposals/")) p.setIconUrl(s.getImageUrl());
        }
        p.setTotalStickers(current.size()+images.size()); packs.saveAndFlush(p);
        log(actor,"STICKERS_UPLOADED",id,Integer.toString(images.size()));
    }

    @Transactional
    public void updateSticker(UUID actor,String pack,String id,String name,String keywords,int order,MultipartFile file) {
        requireAdmin(actor); StickerPack p=locked(pack);
        Sticker s=stickers.findById(id).filter(it -> !it.isRemoved() && it.getPack().getId().equals(pack)).orElseThrow(() -> invalid("Không tìm thấy nhãn dán trong bộ."));
        String label=text(name,100,true,"Tên sticker"), tags=text(keywords,300,false,"Từ khóa");
        if(order<0 || order>9999) throw invalid("Thứ tự phải từ 0 đến 9999.");
        if(file!=null && !file.isEmpty()) {
            if(p.isEverPublished()) throw invalid("Ảnh đã xuất bản không được thay thế.");
            String old=s.getImageUrl();s.setImageUrl(store(StickerImageValidator.validate(file)));s.setRenderType(Sticker.RenderType.IMAGE);
            if (p.getIconUrl().equals(old)) p.setIconUrl(s.getImageUrl());
            deleteAfterCommit(old);
        }
        s.setName(label);s.setKeywords(tags);s.setSortOrder(order);stickers.saveAndFlush(s);
        log(actor,"STICKER_UPDATED",pack,id);
    }

    @Transactional
    public void deleteSticker(UUID actor,String pack,String id) {
        requireAdmin(actor);StickerPack p=locked(pack);
        Sticker s=stickers.findById(id).filter(it -> !it.isRemoved() && it.getPack().getId().equals(pack)).orElseThrow(() -> invalid("Không tìm thấy nhãn dán trong bộ."));
        String old=s.getImageUrl();
        if (p.isEverPublished()) { s.setRemoved(true); stickers.saveAndFlush(s); }
        else { stickers.delete(s); stickers.flush(); }
        var remaining=stickers.findByPackIdOrderBySortOrderAsc(pack);p.setTotalStickers(remaining.size());
        if(p.getIconUrl().equals(old)) p.setIconUrl(remaining.isEmpty()?"/logo-proposals/aloute-sticker-icon.svg":remaining.get(0).getImageUrl());
        packs.saveAndFlush(p);if (!p.isEverPublished() && !stickers.existsByImageUrl(old)) deleteAfterCommit(old);log(actor,"STICKER_DELETED",pack,id);
    }

    @Transactional
    public void publish(UUID actor,String id) {
        requireAdmin(actor);StickerPack p=locked(id);var items=stickers.findByPackIdOrderBySortOrderAsc(id);
        if(items.isEmpty() || items.stream().anyMatch(s -> s.getRenderType()==Sticker.RenderType.LEGACY)) throw invalid("Bộ phải có ít nhất một ảnh sticker hợp lệ trước khi xuất bản.");
        if(p.getStatus()==StickerPack.Status.PUBLISHED) return;
        p.setStatus(StickerPack.Status.PUBLISHED);p.setEverPublished(true);p.setTotalStickers(items.size());packs.saveAndFlush(p);
        log(actor,"STICKER_PACK_PUBLISHED",id,p.getName());
    }
    @Transactional
    public void retire(UUID actor,String id) {
        requireAdmin(actor);StickerPack p=locked(id);
        if(p.getStatus()!=StickerPack.Status.PUBLISHED) throw invalid("Chỉ ngừng cung cấp bộ đang xuất bản.");
        p.setStatus(StickerPack.Status.RETIRED);packs.saveAndFlush(p);log(actor,"STICKER_PACK_RETIRED",id,p.getName());
    }

    @Transactional(readOnly=true)
    public List<StaffMember> searchRecipients(UUID actor,String query) {
        requireAdmin(actor);
        if(query==null || query.strip().length()<2) return List.of();
        return admin.search(query).stream().filter(StaffMember::isActive).toList();
    }
    public record Recipient(UUID id,String username,String displayName,String reason,Instant grantedAt,Instant revokedAt) {}
    @Transactional(readOnly=true)
    public List<Recipient> recipients(UUID actor,String id) {
        requireAdmin(actor);
        return jdbc.query("select u.id,u.username,p.display_name,g.grant_reason,g.installed_at,g.revoked_at from user_sticker_packs g join users u on u.id=g.user_id join profiles p on p.user_id=u.id where g.pack_id=? order by g.installed_at desc,u.id",
                (r,i)->new Recipient(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant(),r.getTimestamp(6)==null?null:r.getTimestamp(6).toInstant()), id);
    }

    @Transactional
    public int grant(UUID actor,String id,Collection<UUID> recipients,String reason) {
        requireAdmin(actor);StickerPack p=locked(id);
        if(p.getAccessType()!=StickerPack.AccessType.AWARD || p.getStatus()!=StickerPack.Status.PUBLISHED) throw invalid("Chỉ cấp bộ phần thưởng đang xuất bản.");
        String why=text(reason,300,true,"Lý do nhận quà");
        if(recipients==null || recipients.isEmpty() || recipients.size()>100 || recipients.stream().anyMatch(Objects::isNull)) throw invalid("Chọn từ 1 đến 100 người nhận.");
        var ids=new LinkedHashSet<>(recipients);
        // Validate the complete batch before writing any entitlement or notification.
        for(UUID user:ids) if(users.findById(user).filter(u -> u.isActive()).isEmpty()) throw invalid("Danh sách có tài khoản không tồn tại hoặc không hoạt động. Chưa cấp quà cho ai.");
        int granted=0;
        for(UUID user:ids) {
            int changed=jdbc.update("insert into user_sticker_packs (id,user_id,pack_id,installed_at,grant_source,granted_by,grant_reason) values (?,?,?,?,'AWARD',?,?) "
                    + "on conflict (user_id,pack_id) do update set installed_at=excluded.installed_at,grant_source='AWARD',granted_by=excluded.granted_by,grant_reason=excluded.grant_reason,revoked_at=null,revoked_by=null where user_sticker_packs.revoked_at is not null",
                    UUID.randomUUID(),user,id,Timestamp.from(clock.instant()),actor,why);
            if(changed>0) { granted++;notifications.stickerAwarded(actor,user,id,p.getName(),why);audit.log(actor,"STICKER_AWARDED","USER",user,id+" · "+why); }
        }
        return granted;
    }
    @Transactional
    public void revoke(UUID actor,String id,UUID user) {
        requireAdmin(actor);StickerPack p=locked(id);
        if(p.getAccessType()!=StickerPack.AccessType.AWARD) throw invalid("Chỉ thu hồi bộ phần thưởng.");
        int changed=jdbc.update("update user_sticker_packs set revoked_at=?,revoked_by=? where user_id=? and pack_id=? and revoked_at is null",Timestamp.from(clock.instant()),actor,user,id);
        if(changed==0)return;
        jdbc.update("delete from user_sticker_favorites where user_id=? and sticker_id in(select id from stickers where pack_id=?)",user,id);
        jdbc.update("delete from user_sticker_recent where user_id=? and sticker_id in(select id from stickers where pack_id=?)",user,id);
        audit.log(actor,"STICKER_REVOKED","USER",user,id);
    }

    private String store(StickerImageValidator.Image image) {
        String url=storage.storeBytes(image.bytes(),"stickers",image.extension());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if(status!=STATUS_COMMITTED) cleanup(url); }
        });
        return url;
    }
    private void deleteAfterCommit(String url) {
        if(url==null || url.startsWith("/stickers/") || url.startsWith("/logo-proposals/"))return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { cleanup(url); } });
    }
    private void cleanup(String url) { try { storage.delete(url); } catch(RuntimeException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Không dọn được ảnh sticker {}",url,e); } }
}
