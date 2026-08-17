<?php

return [
    'commands' => [
        'car:cleanup' => app\command\CleanupCommand::class,
        'car:upgrade' => app\command\UpgradeCommand::class,
        'car:payment-reconcile' => app\command\PaymentReconcileCommand::class,
        'car:admin-reset' => app\command\AdminResetCommand::class,
    ],
];
