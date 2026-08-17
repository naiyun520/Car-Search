<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use think\facade\Db;

class FeedbackAdminController extends BaseController
{
    public function index()
    {
        $rows = Db::name('feedback')->alias('f')->leftJoin('user u','u.id=f.user_id')->field('f.id,f.user_id,f.type,f.content,f.contact,f.status,f.reply,f.replied_at,f.created_at,f.updated_at,u.nickname')->order('f.id','desc')->paginate(['list_rows'=>20,'page'=>max(1,(int)$this->request->get('page',1))])->toArray();
        foreach ($rows['data'] as &$row) { $row['id']=(int)$row['id']; $row['user_id']=(int)$row['user_id']; }
        return $this->ok($rows);
    }

    public function detail(int $id)
    {
        $row = Db::name('feedback')->alias('f')->leftJoin('user u','u.id=f.user_id')->field('f.id,f.user_id,f.type,f.content,f.contact,f.status,f.reply,f.replied_at,f.created_at,f.updated_at,u.nickname')->where('f.id',$id)->find();
        if (!$row) return $this->fail('反馈不存在',404,404);
        $row['id']=(int)$row['id']; $row['user_id']=(int)$row['user_id'];
        return $this->ok($row);
    }

    public function delete(int $id)
    {
        $feedback = Db::name('feedback')->where('id',$id)->find();
        if (!$feedback) return $this->fail('反馈不存在',404,404);
        Db::name('feedback')->where('id',$id)->delete();
        $this->audit('feedback.delete', (string) $id, ['user_id'=>$feedback['user_id']]);
        return $this->ok(null,'反馈已删除');
    }

    public function batchDelete()
    {
        $ids = array_values(array_unique(array_filter(array_map('intval',(array)$this->request->param('ids',[])),fn($id)=>$id>0)));
        if (!$ids) return $this->fail('请选择需要删除的反馈');
        if (count($ids) > 100) return $this->fail('单次最多删除100条反馈');
        $existingIds = Db::name('feedback')->whereIn('id',$ids)->column('id');
        if (!$existingIds) return $this->fail('所选反馈不存在');
        $deleted = Db::name('feedback')->whereIn('id',$existingIds)->delete();
        $this->audit('feedback.batch_delete', implode(',',$existingIds), ['count'=>$deleted]);
        return $this->ok(['deleted'=>$deleted],"已删除{$deleted}条反馈");
    }

    public function reply(int $id)
    {
        $feedback = Db::name('feedback')->where('id',$id)->find();
        if (!$feedback) return $this->fail('反馈不存在',404,404);
        $reply = trim((string)$this->request->post('reply',''));
        if ($reply === '' || mb_strlen($reply) > 1000) return $this->fail('回复内容不能为空且不能超过1000字');
        $now = date('Y-m-d H:i:s');
        Db::name('feedback')->where('id',$id)->update(['reply'=>$reply,'status'=>'resolved','replied_at'=>$now,'updated_at'=>$now]);
        $this->audit('feedback.reply', (string) $id, ['status'=>'resolved']);
        return $this->ok(null,'回复已发送，用户可在投诉意见中查看');
    }

    private function audit(string $action, string $targetId, array $detail): void
    {
        Db::name('audit_log')->insert(['admin_id'=>$this->request->admin['id'],'action'=>$action,'target_type'=>'feedback','target_id'=>$targetId,'detail'=>json_encode($detail,JSON_UNESCAPED_UNICODE),'ip'=>$this->request->ip(),'created_at'=>date('Y-m-d H:i:s')]);
    }
}
