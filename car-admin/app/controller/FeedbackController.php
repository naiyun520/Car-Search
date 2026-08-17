<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use think\facade\Db;

class FeedbackController extends BaseController
{
    public function index()
    {
        $rows = Db::name('feedback')->where('user_id',$this->request->user['id'])->field('id,type,content,contact,status,reply,replied_at,created_at,updated_at')->order('id','desc')->paginate(['list_rows'=>max(1,min(30,(int)$this->request->get('page_size',20))),'page'=>max(1,(int)$this->request->get('page',1))])->toArray();
        return $this->ok($rows);
    }

    public function detail(int $id)
    {
        $row = Db::name('feedback')->where('id',$id)->where('user_id',$this->request->user['id'])->field('id,type,content,contact,status,reply,replied_at,created_at,updated_at')->find();
        if (!$row) return $this->fail('反馈不存在',404,404);
        return $this->ok($row);
    }

    public function create()
    {
        $content = trim((string)$this->request->post('content',''));
        if (mb_strlen($content) < 5 || mb_strlen($content) > 1000) return $this->fail('反馈内容需为5-1000字');
        $id = Db::name('feedback')->insertGetId(['user_id'=>$this->request->user['id'],'type'=>mb_substr((string)$this->request->post('type','suggestion'),0,30),'content'=>$content,'contact'=>mb_substr(trim((string)$this->request->post('contact','')),0,100),'status'=>'pending','created_at'=>date('Y-m-d H:i:s'),'updated_at'=>date('Y-m-d H:i:s')]);
        return $this->ok(['id'=>$id],'反馈已提交，我们会尽快处理');
    }
}
