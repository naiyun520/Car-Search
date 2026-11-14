package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.security.TestNetworkGuard;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedFulfillmentTest {
 JdbcTemplate db; CryptoService crypto; QueryJobService jobs; QueryJobScheduler scheduler;
 QueryCompletionService completion; ProviderService provider; EmailNotificationService email;
 PaymentDeliveryService delivery; ProviderCallService calls; QueryRunnerService runner;
 @BeforeEach void setup() throws Exception {
  var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  db=new JdbcTemplate(ds);
  // 保留应用时钟的纳秒精度，避免 H2 默认微秒四舍五入把刚入队时间移到扫描时间之后。
  db.execute("create table ci_order(id bigint primary key,order_no varchar,status varchar,service_id int,service_snapshot_cipher varchar,input_cipher varchar,cost_amount decimal(12,2),query_attempts int default 0,fulfillment_version int default 0,provider_code varchar,provider_request_id varchar,query_last_error varchar,result_cipher varchar,queried_at timestamp(9),user_visible_until timestamp(9),admin_retention_until timestamp(9),updated_at timestamp(9))");
  db.execute("create table ci_service(id int primary key,query_job_enabled int,query_mode varchar,callback_mode varchar,callback_timeout_sec int,provider_api_id int,poll_interval_sec int,poll_timeout_sec int,poll_max_attempts int,result_schema varchar)");
  db.execute("create table ci_query_job(id bigint auto_increment primary key,order_id bigint unique,service_id int,provider_id int,job_type varchar,state varchar,submit_attempts int,fetch_attempts int,max_fetch_attempts int,interval_seconds int,next_run_at timestamp(9),deadline_at timestamp(9),version int,created_at timestamp(9),updated_at timestamp(9),lock_owner varchar,locked_until timestamp(9),provider_request_id varchar,last_error_class varchar,last_error_code varchar,last_error_message varchar,completed_at timestamp(9))");
  db.update("insert into ci_service values(1,0,'async','none',120,2,60,1200,2,'{\"name\":\"报告说明\"}')");
  var props=mock(CarProperties.class);when(props.dataEncryptKey()).thenReturn("ONLY-LOCAL-UNIT-TEST-KEY-2026");crypto=new CryptoService(new ObjectMapper(),props);
  db.update("insert into ci_order(id,order_no,status,service_id,input_cipher) values(1,'LOCAL','paid',1,?)",crypto.encrypt(Map.of("vin","LOCAL")));
  provider=mock(ProviderService.class);email=mock(EmailNotificationService.class);delivery=mock(PaymentDeliveryService.class);calls=mock(ProviderCallService.class);
  completion=new QueryCompletionService(db,crypto,email,delivery,new DataSourceTransactionManager(ds));
  jobs=new QueryJobService(db,crypto,completion,true);runner=new QueryRunnerService(db,jobs);
  scheduler=new QueryJobScheduler(db,crypto,jobs,provider,calls,completion);
  when(provider.submitAsync(anyMap(),anyMap())).thenReturn(new ProviderService.SubmitResult("200","LOCAL-REQ"));
  when(provider.fetchReport(anyMap(),anyString())).thenReturn(new ProviderService.FetchResult(ProviderService.FetchState.SUCCESS,"200","LOCAL-REQ",List.of(Map.of("key","name","label","说明","value","LOCAL-REPORT")),""));
 }
 Map<String,Object> order(){return db.queryForMap("select * from ci_order where id=1");}
 Map<String,Object> task(){return db.queryForMap("select * from ci_query_job where order_id=1");}
 void submit(){runner.run(order());scheduler.runDueJobs();}
 void due(){db.update("update ci_query_job set next_run_at=?",LocalDateTime.now().minusSeconds(1));scheduler.runDueJobs();}
 @Test void allServicesUseTasksEvenWithFormerServiceSwitchOff(){runner.run(order());runner.run(order());assertEquals(1,db.queryForObject("select count(*) from ci_query_job",Integer.class));assertEquals(1,order().get("query_attempts"));verifyNoInteractions(provider);}
 @Test void asyncDoesNotFetchImmediatelyAndCompletesOnce() throws Exception {submit();verify(provider).submitAsync(anyMap(),anyMap());verify(provider,never()).fetchReport(anyMap(),anyString());assertEquals("waiting_result",task().get("state"));assertTrue(((java.sql.Timestamp)task().get("next_run_at")).toLocalDateTime().isAfter(LocalDateTime.now().plusSeconds(55)));due();assertEquals("success",order().get("status"));assertEquals(1,task().get("fetch_attempts"));assertTrue(crypto.decrypt(order().get("result_cipher").toString()).toString().contains("LOCAL-REPORT"));assertFalse(completion.completeSuccess(task(),List.of(),"200","LOCAL-REQ"));verify(email,times(1)).querySucceeded(1L);verify(delivery,times(1)).deliver("LOCAL",false);}
 @Test void twentyAttemptsWithoutTimeLimitStopsAndNotifiesOnce() throws Exception {
  db.update("update ci_service set poll_timeout_sec=0,poll_max_attempts=20");
  when(provider.fetchReport(anyMap(),anyString())).thenReturn(new ProviderService.FetchResult(ProviderService.FetchState.RUNNING,"200","LOCAL-REQ",List.of(),""));
  submit();assertNull(task().get("deadline_at"));
  for(int i=0;i<20;i++)due();
  assertEquals(20,task().get("fetch_attempts"));assertEquals("timed_out",task().get("state"));
  due();verify(provider,times(20)).fetchReport(anyMap(),eq("LOCAL-REQ"));verify(provider,times(1)).submitAsync(anyMap(),anyMap());
  verify(email,times(1)).queryFailed(eq(1L),eq("timeout"),eq("LOCAL-REQ"),anyString());
 }
 @Test void obsoleteCallbackSettingCannotEnableCallbackExecution(){db.update("update ci_service set callback_mode='callback'");submit();assertEquals("async",task().get("job_type"));assertEquals("waiting_result",task().get("state"));}
 @Test void rejectedWorkerReturnsClaimWithoutCallingSupplier(){
  QueryJobScheduler rejecting=new QueryJobScheduler(db,crypto,jobs,provider,calls,completion,r->{throw new java.util.concurrent.RejectedExecutionException();},1);
  rejecting.runDueJobs();assertEquals("pending_submit",task().get("state"));verifyNoInteractions(provider);
  scheduler.runDueJobs();assertEquals("waiting_result",task().get("state"));
 }

 @Test void synchronousWorkerFailureCannotReturnAnAlreadySubmittedClaim() throws Exception {
  when(provider.submitAsync(anyMap(),anyMap())).thenThrow(new java.net.SocketTimeoutException("uncertain"));
  doThrow(new IllegalStateException("mail queue unavailable")).when(email).queryFailed(any(),anyString(),anyString(),anyString());
  scheduler.runDueJobs();
  assertEquals("submitting",task().get("state"));
  scheduler.runDueJobs();
  verify(provider,times(1)).submitAsync(anyMap(),anyMap());
 }
 @Test void workerCapacityDoesNotClaimMoreThanCanStart(){
  List<Runnable> waiting=new ArrayList<>();
  QueryJobScheduler bounded=new QueryJobScheduler(db,crypto,jobs,provider,calls,completion,waiting::add,1);
  bounded.runDueJobs();bounded.runDueJobs();assertEquals(1,waiting.size());verifyNoInteractions(provider);
  waiting.removeFirst().run();assertEquals("waiting_result",task().get("state"));
 }
 @Test void uncertainSubmissionAndRetryNeverSubmitAgain() throws Exception {when(provider.submitAsync(anyMap(),anyMap())).thenThrow(new java.net.SocketTimeoutException("LOCAL"));submit();runner.retry("LOCAL");scheduler.runDueJobs();assertEquals("submit_unknown",task().get("state"));verify(provider,times(1)).submitAsync(anyMap(),anyMap());}
 @Test void deadlineCheckedBeforeNetwork() throws Exception {submit();db.update("update ci_query_job set deadline_at=?",LocalDateTime.now().minusSeconds(1));due();verify(provider,never()).fetchReport(anyMap(),anyString());assertEquals("timed_out",task().get("state"));}
 @Test void fetchAttemptsIncludeLastRunningAttempt() throws Exception {when(provider.fetchReport(anyMap(),anyString())).thenReturn(new ProviderService.FetchResult(ProviderService.FetchState.RUNNING,"200","LOCAL-REQ",List.of(),""));submit();due();due();assertEquals(2,task().get("fetch_attempts"));assertEquals("timed_out",task().get("state"));verify(provider,times(2)).fetchReport(anyMap(),anyString());}
 @Test void lostLeaseCannotCallSupplier() throws Exception {runner.run(order());var stale=jobs.claimDueJobs(1,"one").getFirst();db.update("update ci_query_job set version=version+1,lock_owner='two'");scheduler.process(stale);verifyNoInteractions(provider);}
 @Test void paidOrderRecoversWithoutClient(){scheduler.runDueJobs();var job=task();assertEquals("waiting_result",job.get("state"),()->"next_run_at="+job.get("next_run_at")+", now="+LocalDateTime.now());}
 @Test void stoppingTaskSendsFailureAndPreventsLateSuccess(){submit();jobs.stop(1,"人工停止");assertEquals("stopped",task().get("state"));assertFalse(completion.completeSuccess(task(),List.of(),"200","LOCAL-REQ"));verify(email).queryFailed(eq(1L),eq("stopped"),eq("LOCAL-REQ"),anyString());}
 @Test void refundedOrderCannotBeOverwritten(){submit();db.update("update ci_order set status='refunded'");assertFalse(completion.completeSuccess(task(),List.of(),"200","LOCAL-REQ"));assertFalse(completion.completeFailure(task(),"failed","error","LOCAL-REQ","late"));assertEquals("refunded",order().get("status"));verifyNoInteractions(email);}
 @Test void resultAndNotificationCommitTogether(){submit();doThrow(new IllegalStateException("queue unavailable")).when(email).querySucceeded(any());assertThrows(IllegalStateException.class,()->completion.completeSuccess(task(),List.of(),"200","LOCAL-REQ"));assertEquals("querying",order().get("status"));assertEquals("waiting_result",task().get("state"));}
 @Test void inFlightServiceUsesSnapshot() throws Exception {var snapshot=db.queryForMap("select * from ci_service where id=1");db.update("update ci_order set service_snapshot_cipher=?",crypto.encrypt(snapshot));db.update("update ci_service set provider_api_id=999");submit();var arg=org.mockito.ArgumentCaptor.forClass(Map.class);verify(provider).submitAsync(arg.capture(),anyMap());assertEquals(2,((Number)arg.getValue().get("provider_api_id")).intValue());}
 @Test void pausedSchedulerStillQueuesAndNeverFallsBack(){var paused=new QueryJobService(db,crypto,completion,false);var r=new QueryRunnerService(db,paused);r.run(order());new QueryJobScheduler(db,crypto,paused,provider,calls,completion).runDueJobs();assertEquals("pending_submit",task().get("state"));verifyNoInteractions(provider);}
 @Test void reportParserRejectsMissingAndNestedFailure() throws Exception {var real=new ProviderService(crypto,new ObjectMapper(),db);var s=Map.<String,Object>of("result_schema","{\"name\":\"说明\"}");assertEquals(ProviderService.FetchState.NO_DATA,real.parseReport(s,Map.of("number",200,"result",Map.of("order_status",1)),"REQ").state());assertEquals(ProviderService.FetchState.FAILED,real.parseReport(s,Map.of("number",200,"result",Map.of("result",Map.of("order_status",2))),"REQ").state());assertNotEquals(ProviderService.FetchState.SUCCESS,real.parseReport(s,Map.of(),"REQ").state());}
 @Test void localNetworkPolicyCannotReachRealSupplier(){assertTrue(TestNetworkGuard.allowedSupplier(java.net.URI.create("http://testapi.naiblog.cn/path")));assertFalse(TestNetworkGuard.allowedSupplier(java.net.URI.create("http://127.0.0.1:18767/submit")));assertFalse(TestNetworkGuard.allowedSupplier(java.net.URI.create("http://open-api2.0.nanxinwang.com/Car/transferOrder")));assertFalse(TestNetworkGuard.allowedSupplier(java.net.URI.create("http://testapi.naiblog.cn.evil.example/")));assertFalse(TestNetworkGuard.allowedSupplier(java.net.URI.create("http://user:pass@testapi.naiblog.cn/")));}

 void prepareDirectBatch(int count)throws Exception{
  db.update("update ci_service set query_mode='single'");
  for(int i=1;i<=count;i++){
   if(i>1)db.update("insert into ci_order(id,order_no,status,service_id,input_cipher) values(?,?,'paid',1,?)",i,"LOCAL-"+i,crypto.encrypt(Map.of("vin","LOCAL-"+i)));
   runner.run(db.queryForMap("select * from ci_order where id=?",i));
  }
  when(provider.executeDirect(anyMap(),anyMap())).thenAnswer(c->new ProviderService.Result("200",String.valueOf(((Map<?,?>)c.getArgument(1)).get("vin")),List.of(Map.of("key","report","label","结果","value","LOCAL"))));
 }
 @Test void oneDispatchDrainsOneHundredOrdersWithoutWaitingForAnotherTick()throws Exception{
  prepareDirectBatch(100);scheduler.runDueJobs();
  assertEquals(100,db.queryForObject("select count(*) from ci_order where status='success'",Integer.class));
  verify(provider,times(100)).executeDirect(anyMap(),anyMap());
  scheduler.runDueJobs();verify(provider,times(100)).executeDirect(anyMap(),anyMap());
 }
 @Test void drainingNeverExceedsFourWorkersOrDuplicatesAQuery()throws Exception{
  prepareDirectBatch(100);var pool=java.util.concurrent.Executors.newFixedThreadPool(4);
  var active=new java.util.concurrent.atomic.AtomicInteger();var maximum=new java.util.concurrent.atomic.AtomicInteger();
  when(provider.executeDirect(anyMap(),anyMap())).thenAnswer(c->{int n=active.incrementAndGet();maximum.accumulateAndGet(n,Math::max);try{Thread.sleep(15);return new ProviderService.Result("200",String.valueOf(((Map<?,?>)c.getArgument(1)).get("vin")),List.of(Map.of("key","report","label","结果","value","LOCAL")));}finally{active.decrementAndGet();}});
  try{
   var parallel=new QueryJobScheduler(db,crypto,jobs,provider,calls,completion,pool,4);parallel.runDueJobs();
   long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
   while(db.queryForObject("select count(*) from ci_order where status='success'",Integer.class)<100&&System.nanoTime()<until)Thread.sleep(20);
   assertEquals(100,db.queryForObject("select count(*) from ci_order where status='success'",Integer.class));assertTrue(maximum.get()<=4);assertTrue(maximum.get()>1);verify(provider,times(100)).executeDirect(anyMap(),anyMap());
  }finally{pool.shutdownNow();}
 }
 @Test void shutdownDoesNotClaimAnotherOrderAfterItsCurrentRequest()throws Exception{
  prepareDirectBatch(100);
  when(provider.executeDirect(anyMap(),anyMap())).thenAnswer(c->{scheduler.stopDraining();return new ProviderService.Result("200","LOCAL",List.of(Map.of("key","report","label","结果","value","LOCAL")));});
  scheduler.runDueJobs();verify(provider,times(1)).executeDirect(anyMap(),anyMap());
  assertEquals(99,db.queryForObject("select count(*) from ci_query_job where state='pending_direct'",Integer.class));
 }
}
