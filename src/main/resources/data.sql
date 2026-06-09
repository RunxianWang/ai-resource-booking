INSERT IGNORE INTO resource_machine (
    id,
    machine_name,
    resource_type,
    gpu_model,
    status
) VALUES
(1, 'H100-Node-01', 'GPU', 'H100', 'ACTIVE'),
(2, 'H100-Node-02', 'GPU', 'H100', 'ACTIVE'),
(3, 'A100-Node-01', 'GPU', 'A100', 'ACTIVE');
