<?php

use app\middleware\AdminAuth;
use app\middleware\UserAuth;
use think\facade\Route;

Route::get('api/v1/bootstrap','PublicController/bootstrap');
Route::get('api/v1/service-icon/:file','PublicController/serviceIcon');
Route::post('api/v1/auth/login','AuthController/login');

Route::group('api/v1', function () {
    Route::get('me','AuthController/me');
    Route::post('me/profile','AuthController/updateProfile');
    // Use explicit action paths only. A base POST /checkout rule can prefix-match
    // /checkout/confirm and dispatch payment confirmation to the create action.
    Route::post('checkout/create','CheckoutController/create');
    Route::get('checkout/status','CheckoutController/status');
    Route::post('checkout/payment','CheckoutController/payment');
    Route::post('checkout/confirm','CheckoutController/confirm');
    Route::post('checkout/query','CheckoutController/query');
    Route::get('checkout/recoverable','CheckoutController/recoverable');
    Route::get('orders','OrderController/index');
    Route::post('feedback','FeedbackController/create');
    Route::get('feedback','FeedbackController/index');
    Route::get('feedback-detail/:id','FeedbackController/detail');
})->middleware(UserAuth::class);

Route::post('admin-api/login','AdminController/login');
Route::group('admin-api', function () {
    Route::get('dashboard','AdminController/dashboard');
    Route::get('dashboard-finance','AdminController/dashboardFinance');
    Route::get('users','AdminController/users');
    Route::post('users/:id/status','AdminController/setUserStatus');
    Route::get('orders','AdminController/orders');
    Route::post('orders/batch-delete','AdminController/deleteOrders');
    // Keep detail off the collection prefix. ThinkPHP can prefix-match the
    // collection route and otherwise return the paginated list here.
    Route::get('order-detail/:orderNo','AdminController/orderDetail');
    Route::post('orders/:orderNo/sync-payment','AdminController/syncPayment');
    Route::post('orders/:orderNo/retry','AdminController/retryOrder');
    Route::post('orders/:orderNo/delivery','AdminController/retryDelivery');
    Route::post('orders/:orderNo/refund','AdminController/refundOrder');
    Route::post('orders/:orderNo/refund-status','AdminController/reconcileRefund');
    Route::post('orders/:orderNo/manual-refund','AdminController/manualRefundOrder');
    Route::get('services','ServiceAdminController/index');
    Route::post('service-test/:id','ServiceAdminController/test');
    Route::post('service-icon-upload/:id','ServiceAdminController/uploadIcon');
    Route::post('services/sort','ServiceAdminController/sort');
    Route::post('services/:id/update','ServiceAdminController/update');
    Route::post('services/:id','ServiceAdminController/update');
    Route::delete('services/:id','ServiceAdminController/delete');
    Route::post('services','ServiceAdminController/create');
    Route::get('announcements','AdminController/announcements');
    Route::post('announcements','AdminController/saveAnnouncement');
    Route::get('settings','AdminController/settings');
    Route::post('settings','AdminController/saveSettings');
    Route::get('payment-settings','AdminController/paymentSettings');
    Route::post('payment-settings','AdminController/savePaymentSettings');
    Route::get('feedback','FeedbackAdminController/index');
    Route::delete('feedback/batch','FeedbackAdminController/batchDelete');
    Route::get('feedback-detail/:id','FeedbackAdminController/detail');
    Route::post('feedback/:id/reply','FeedbackAdminController/reply');
    Route::delete('feedback/:id','FeedbackAdminController/delete');
    Route::post('password','AdminController/changePassword');
    Route::get('email-settings','AdminController/emailSettings');
    Route::post('email-settings','AdminController/saveEmailSettings');
    Route::post('email-test','AdminController/testEmail');
})->middleware(AdminAuth::class);
