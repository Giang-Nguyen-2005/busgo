package com.busgo.media;

import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OperatorMediaService {
    public enum Category { INTERIOR, EXTERIOR, SEAT, OTHER }
    public record Photo(String id, long busTypeId, String category, String alt, boolean featured, String url) {}
    public record Gallery(String logoUrl, List<Photo> photos) {}
    private final JdbcTemplate db;
    private final OperatorContextService context;
    private static final String AVAILABLE = """
        EXISTS (SELECT 1 FROM buses b JOIN bus_types bt ON bt.id=b.bus_type_id
          WHERE b.operator_id=m.operator_id AND b.bus_type_id=m.bus_type_id AND b.deleted_at IS NULL
          AND b.status='AVAILABLE' AND bt.status='ACTIVE')
        """;

    public OperatorMediaService(JdbcTemplate db, OperatorContextService context) {
        this.db=db;
        this.context=context;
    }
    private static BusinessException missing() {
        return new BusinessException("MEDIA_NOT_FOUND","Không tìm thấy hình ảnh.",HttpStatus.NOT_FOUND,null);
    }
    private static String url(long owner,String id) {
        return "/api/v1/public/operators/"+owner+"/media/"+id;
    }
    private long owner(CurrentUser user) {
        long owner=context.requireAdminOperator(user).getId();
        db.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,owner);
        return owner;
    }
    private void requireBus(long owner,long type) {
        if (!Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM buses b JOIN bus_types bt ON bt.id=b.bus_type_id
            WHERE b.operator_id=? AND b.bus_type_id=? AND b.deleted_at IS NULL AND b.status<>'INACTIVE' AND bt.status='ACTIVE')
            """,Boolean.class,owner,type))) throw new BusinessException("MEDIA_TYPE_NOT_OPERATED",
                "Nhà xe chưa vận hành loại xe này.",HttpStatus.BAD_REQUEST,null);
    }
    private List<Photo> photos(long owner, Long type, boolean visible) {
        String sql="SELECT id,bus_type_id,category,alt,featured FROM operator_media m WHERE operator_id=? AND bus_type_id IS NOT NULL";
        List<Object> args=new ArrayList<>(List.of(owner));
        if(type!=null) { sql+=" AND bus_type_id=?"; args.add(type); }
        if(visible) sql+=" AND "+AVAILABLE;
        return db.query(sql+" ORDER BY bus_type_id,position,id",(r,n)->new Photo(r.getString(1),r.getLong(2),
            r.getString(3),r.getString(4),r.getBoolean(5),url(owner,r.getString(1))),args.toArray());
    }
    @Transactional(readOnly=true)
    public List<Photo> own(CurrentUser user,long type) {
        long owner=context.requireAdminOperator(user).getId();
        var existing=photos(owner,type,false);
        if(existing.isEmpty()) requireBus(owner,type);
        return existing;
    }
    @Transactional(readOnly=true)
    public Gallery gallery(long owner) {
        var logos=db.query("SELECT logo_url FROM transport_operators WHERE id=? AND status='ACTIVE'",(r,n)->r.getString(1),owner);
        if(logos.isEmpty()) throw missing();
        return new Gallery(logos.get(0),photos(owner,null,true));
    }
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> file(long owner,UUID id) {
        return db.query("""
            SELECT mime,content FROM operator_media m JOIN transport_operators op ON op.id=m.operator_id
            WHERE op.id=? AND op.status='ACTIVE' AND m.id=? AND
            ((m.category='LOGO' AND op.logo_url=?) OR
            """+AVAILABLE+")",this::image,owner,id.toString(),url(owner,id.toString())).stream().findFirst().orElseThrow(OperatorMediaService::missing);
    }
    private String insert(long owner,Long type,String category,String alt,int position,boolean featured,MultipartFile file) {
        var image=ImageUpload.read(file);
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO operator_media(id,operator_id,bus_type_id,category,alt,position,featured,mime,content) VALUES(?,?,?,?,?,?,?,?,?)",
            id,owner,type,category,alt,position,featured,image.mime(),image.bytes());
        return id;
    }
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> previewLogo(CurrentUser user) {
        long owner=context.requireAdminOperator(user).getId();
        return db.query("SELECT mime,content FROM operator_media WHERE operator_id=? AND category='LOGO'",
            this::image,owner).stream().findFirst().orElseThrow(OperatorMediaService::missing);
    }
    private ResponseEntity<byte[]> image(java.sql.ResultSet row,int index) throws java.sql.SQLException {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(row.getString(1)))
            .body(row.getBytes(2));
    }
    @Transactional(readOnly=true)
    public ResponseEntity<byte[]> preview(CurrentUser user,long type,UUID id) {
        long owner=context.requireAdminOperator(user).getId();
        return db.query("SELECT mime,content FROM operator_media WHERE operator_id=? AND bus_type_id=? AND id=?",
            this::image,owner,type,id.toString())
            .stream().findFirst().orElseThrow(OperatorMediaService::missing);
    }
    public void logo(CurrentUser user,MultipartFile file) {
        long owner=owner(user);
        db.update("DELETE FROM operator_media WHERE operator_id=? AND category='LOGO'",owner);
        String logo=file==null?null:url(owner,insert(owner,null,"LOGO","",0,false,file));
        db.update("UPDATE transport_operators SET logo_url=?,updated_at=UTC_TIMESTAMP(6) WHERE id=?",logo,owner);
    }
    public void upload(CurrentUser user,long type,Category category,String alt,List<MultipartFile> files) {
        long owner=owner(user);
        requireBus(owner,type);
        var existing=photos(owner,type,false);
        if(files.isEmpty() || existing.size()+files.size()>12) throw ImageUpload.invalid("Mỗi loại xe có tối đa 12 ảnh.");
        int position=existing.size();
        for(var file:files) {
            insert(owner,type,category.name(),alt,position,position==0,file);
            position++;
        }
    }
    public void edit(CurrentUser user,long type,UUID id,Category category,String alt,boolean feature) {
        long owner=owner(user);
        requireBus(owner,type);
        var gallery=photos(owner,type,false);
        if(gallery.stream().noneMatch(p->p.id().equals(id.toString()))) throw missing();
        if(feature) db.update("UPDATE operator_media SET featured=(id=?) WHERE operator_id=? AND bus_type_id=?",id.toString(),owner,type);
        db.update("UPDATE operator_media SET alt=?,category=? WHERE operator_id=? AND bus_type_id=? AND id=?",alt,category.name(),owner,type,id.toString());
    }
    public void reorder(CurrentUser user,long type,List<UUID> ids) {
        long owner=owner(user);
        var existing=photos(owner,type,false);
        var ordered=ids.stream().map(UUID::toString).toList();
        if(existing.size()!=ids.size() || !new HashSet<>(ordered).equals(new HashSet<>(existing.stream().map(Photo::id).toList())))
            throw ImageUpload.invalid("Danh sách ảnh đã thay đổi. Hãy tải lại.");
        positions(owner,type,ordered);
    }
    private void positions(long owner,long type,List<String> ids) {
        for(int i=0;i<ids.size();i++) db.update("UPDATE operator_media SET position=? WHERE operator_id=? AND bus_type_id=? AND id=?",i,owner,type,ids.get(i));
    }
    public void delete(CurrentUser user,long type,UUID id) {
        long owner=owner(user);
        if(db.update("DELETE FROM operator_media WHERE operator_id=? AND bus_type_id=? AND id=?",owner,type,id.toString())!=1) throw missing();
        var remaining=photos(owner,type,false);
        positions(owner,type,remaining.stream().map(Photo::id).toList());
        if(!remaining.isEmpty() && remaining.stream().noneMatch(Photo::featured))
            db.update("UPDATE operator_media SET featured=TRUE WHERE id=? AND operator_id=?",remaining.get(0).id(),owner);
    }
}
