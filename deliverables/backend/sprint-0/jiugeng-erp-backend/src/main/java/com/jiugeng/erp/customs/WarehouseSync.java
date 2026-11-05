package com.jiugeng.erp.customs;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Compatibility response for older clients. Warehouse addresses are maintained manually. */
@Service @Profile("!mock")
class WarehouseSync {
 record Status(String state,String startedAt,String finishedAt,String nextRunAt,String message,String sourceDate,int added,int updated,int preserved,int rejected,boolean automatic){}
 Status status(){return new Status("DISABLED","","","","网上更新已关闭，请人工维护仓库地址","",0,0,0,0,false);}
 Status start(boolean dueOnly){throw new ResponseStatusException(HttpStatus.GONE,"网上更新已关闭，请人工维护仓库地址");}
}
