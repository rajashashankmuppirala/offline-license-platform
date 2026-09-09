package com.enterprise.licensing.storage.repository;

import com.enterprise.licensing.engine.metering.*;
import com.enterprise.licensing.engine.ports.TransactionRunner;
import com.enterprise.licensing.model.*;
import com.enterprise.licensing.storage.sqlite.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class SqliteMeteringRepository implements MeteringRepository{
 private final SqliteDatabase database;private final TransactionRunner transactions;private final ObjectMapper mapper;
 public SqliteMeteringRepository(SqliteDatabase d,TransactionRunner t,ObjectMapper m){database=d;transactions=t;mapper=m;}
 @Override public UsageResult consume(String request,String customer,String instance,String pool,String meter,String action,long amount,Instant start,Instant end,long limit,Instant now,Map<String,String>dimensions){return transactions.write(()->{
   String dimensionsJson=json(dimensions);var previous=previous(request,customer,instance,pool,meter,action,amount,start,dimensionsJson);if(previous!=null)return previous;
   execute("INSERT INTO meter_usage(customer_id,pool_id,meter_id,period_start,period_end,used_amount,licensed_limit) VALUES(?,?,?,?,?,0,?) ON CONFLICT(customer_id,pool_id,meter_id,period_start) DO UPDATE SET licensed_limit=excluded.licensed_limit,period_end=excluded.period_end",customer,pool,meter,start.toString(),end.toString(),limit);
   int changed=execute("UPDATE meter_usage SET used_amount=used_amount+? WHERE customer_id=? AND pool_id=? AND meter_id=? AND period_start=? AND used_amount+?<=licensed_limit",amount,customer,pool,meter,start.toString(),amount);
   boolean accepted=changed==1;var reason=accepted?LicenseReasonCode.LICENSE_VALID:LicenseReasonCode.AI_USAGE_LIMIT_EXCEEDED;var counter=counter(customer,pool,meter,start);
   execute("INSERT INTO usage_consumption(request_id,customer_id,instance_id,meter_id,period_start,amount,accepted,decided_at,dimensions_json,pool_id,action_type,period_end,licensed_limit,usage_after,reason_code) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",request,customer,instance,meter,start.toString(),amount,accepted?1:0,now.toString(),dimensionsJson,pool,action,end.toString(),counter.licensedLimit(),counter.usedAmount(),reason.name());
   return accepted?UsageResult.accepted(counter):UsageResult.denied(reason,counter);
 });}
 @Override public List<UsageCounter> counters(){return transactions.read(()->database.withConnection(c->{try(var s=c.prepareStatement("SELECT customer_id,pool_id,meter_id,period_start,period_end,used_amount,licensed_limit FROM meter_usage ORDER BY period_start,pool_id,meter_id");var r=s.executeQuery()){var values=new ArrayList<UsageCounter>();while(r.next())values.add(new UsageCounter(r.getString(1),r.getString(2),r.getString(3),Instant.parse(r.getString(4)),Instant.parse(r.getString(5)),r.getLong(6),r.getLong(7)));return List.copyOf(values);}catch(Exception e){throw failure(e);}}));}
 private UsageResult previous(String request,String customer,String instance,String pool,String meter,String action,long amount,Instant start,String dimensionsJson){return database.withConnection(c->{try(var s=c.prepareStatement("SELECT customer_id,instance_id,pool_id,meter_id,action_type,amount,period_start,dimensions_json,accepted,period_end,licensed_limit,usage_after,reason_code FROM usage_consumption WHERE request_id=?")){s.setString(1,request);try(var r=s.executeQuery()){if(!r.next())return null;if(!Objects.equals(customer,r.getString(1))||!Objects.equals(instance,r.getString(2))||!Objects.equals(pool,r.getString(3))||!Objects.equals(meter,r.getString(4))||!Objects.equals(action,r.getString(5))||amount!=r.getLong(6)||!Objects.equals(start.toString(),r.getString(7))||!Objects.equals(dimensionsJson,r.getString(8)))return UsageResult.denied(LicenseReasonCode.INVALID_REQUEST,null);if(r.getString(10)==null||r.getString(13)==null)return UsageResult.denied(LicenseReasonCode.INVALID_REQUEST,null);var snapshot=new UsageCounter(customer,pool,meter,start,Instant.parse(r.getString(10)),r.getLong(12),r.getLong(11));var reason=LicenseReasonCode.valueOf(r.getString(13));return r.getInt(9)==1?UsageResult.accepted(snapshot):UsageResult.denied(reason,snapshot);}}catch(Exception e){throw failure(e);}});}
 private UsageCounter counter(String customer,String pool,String meter,Instant start){return database.withConnection(c->{try(var s=c.prepareStatement("SELECT period_end,used_amount,licensed_limit FROM meter_usage WHERE customer_id=? AND pool_id=? AND meter_id=? AND period_start=?")){s.setString(1,customer);s.setString(2,pool);s.setString(3,meter);s.setString(4,start.toString());try(var r=s.executeQuery()){if(!r.next())return null;return new UsageCounter(customer,pool,meter,start,Instant.parse(r.getString(1)),r.getLong(2),r.getLong(3));}}catch(Exception e){throw failure(e);}});}
 private int execute(String sql,Object...v){return database.withConnection(c->{try(var s=c.prepareStatement(sql)){for(int i=0;i<v.length;i++)s.setObject(i+1,v[i]);return s.executeUpdate();}catch(Exception e){throw failure(e);}});}
 private String json(Map<String,String> v){try{return mapper.writeValueAsString(v==null?Map.of():new TreeMap<>(v));}catch(Exception e){throw failure(e);}}private static SqliteStorageException failure(Exception e){return new SqliteStorageException("Unable to update usage",e);}
}
