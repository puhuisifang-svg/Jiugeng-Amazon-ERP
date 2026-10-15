package com.jiugeng.erp.catalogmedia;

import static com.jiugeng.erp.catalogmedia.CatalogImageGateway.Purpose;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CatalogImageRepository {
    private final JdbcClient jdbc;
    CatalogImageRepository(JdbcClient jdbc){this.jdbc=jdbc;}
    void insert(Asset a){jdbc.sql("""
            INSERT INTO catalog_image_asset (id,purpose,status,original_storage_key,thumbnail_storage_key,
              source_file_name,source_media_type,stored_media_type,byte_size,sha256,pixel_width,pixel_height,
              uploaded_by,created_at) VALUES (:id,:purpose,'PENDING',:original,:thumbnail,:name,:sourceType,
              :storedType,:size,:sha,:width,:height,:actor,:created)
            """).param("id",a.id).param("purpose",a.purpose.name()).param("original",a.originalKey)
            .param("thumbnail",a.thumbnailKey).param("name",a.fileName).param("sourceType",a.sourceMediaType)
            .param("storedType",a.storedMediaType).param("size",a.size).param("sha",a.sha256)
            .param("width",a.width).param("height",a.height).param("actor",a.uploadedBy).param("created",a.createdAt).update();}
    Optional<Asset> find(String id){return jdbc.sql("SELECT * FROM catalog_image_asset WHERE id=:id").param("id",id).query(this::map).optional();}
    int attach(String id,Purpose purpose,String actor,String type,String resource,Instant at){return jdbc.sql("""
            UPDATE catalog_image_asset SET status='ACTIVE',attached_resource_type=:type,attached_resource_id=:resource,
              attached_at=:at WHERE id=:id AND purpose=:purpose AND status='PENDING' AND uploaded_by=:actor
            """).param("id",id).param("purpose",purpose.name()).param("actor",actor).param("type",type)
            .param("resource",resource).param("at",at).update();}
    int release(String id,String type,String resource){return jdbc.sql("""
            UPDATE catalog_image_asset SET status='PENDING',attached_resource_type=NULL,attached_resource_id=NULL,
              attached_at=NULL,created_at=CURRENT_TIMESTAMP WHERE id=:id AND status='ACTIVE'
              AND attached_resource_type=:type AND attached_resource_id=:resource
            """).param("id",id).param("type",type).param("resource",resource).update();}
    List<Asset> orphans(Instant cutoff){return jdbc.sql("SELECT * FROM catalog_image_asset WHERE status='PENDING' AND created_at<:cutoff")
            .param("cutoff",cutoff).query(this::map).list();}
    void deletePending(String id){jdbc.sql("DELETE FROM catalog_image_asset WHERE id=:id AND status='PENDING'").param("id",id).update();}
    private Asset map(ResultSet rs,int n)throws SQLException{return new Asset(rs.getString("id"),Purpose.valueOf(rs.getString("purpose")),rs.getString("status"),rs.getString("original_storage_key"),rs.getString("thumbnail_storage_key"),rs.getString("source_file_name"),rs.getString("source_media_type"),rs.getString("stored_media_type"),rs.getLong("byte_size"),rs.getString("sha256"),rs.getInt("pixel_width"),rs.getInt("pixel_height"),rs.getString("uploaded_by"),rs.getString("attached_resource_type"),rs.getString("attached_resource_id"),rs.getTimestamp("created_at").toInstant());}
    record Asset(String id,Purpose purpose,String status,String originalKey,String thumbnailKey,String fileName,
                 String sourceMediaType,String storedMediaType,long size,String sha256,int width,int height,
                 String uploadedBy,String resourceType,String resourceId,Instant createdAt){}
}
