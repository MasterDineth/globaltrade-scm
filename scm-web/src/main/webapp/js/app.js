// ============================================================================
// GlobalTrade SCM - Frontend Dashboard Logic
// ============================================================================

// --- Authentication State ---
let authToken = sessionStorage.getItem('scm_auth_token');
let authRole = sessionStorage.getItem('scm_auth_role');

// --- DOM Elements ---
const loginOverlay = document.getElementById('login-overlay');
const logoutOverlay = document.getElementById('logout-overlay');
const dashboard = document.getElementById('dashboard');
const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');
const logoutBtn = document.getElementById('logout-btn');
const displayUser = document.getElementById('display-user');

// Modal Elements
const actionModal = document.getElementById('action-modal');
const modalTitle = document.getElementById('modal-title');
const modalDesc = document.getElementById('modal-desc');
const modalInputs = document.getElementById('modal-inputs');
const modalForm = document.getElementById('action-form');
const modalCancel = document.getElementById('modal-cancel');
const modalError = document.getElementById('modal-error');
let currentModalAction = null;

// --- Initialize ---
document.addEventListener('DOMContentLoaded', () => {
    if (authToken) {
        showDashboard();
    } else {
        loginOverlay.classList.remove('hidden');
    }
});

// --- Login Handler ---
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const u = document.getElementById('username').value;
    const p = document.getElementById('password').value;
    const tempAuthHeader = 'Basic ' + btoa(u + ':' + p);

    try {
        const response = await fetch('/scm/api/shipments/active', {
            headers: { 'Authorization': tempAuthHeader }
        });

        if (response.ok) {
            authToken = tempAuthHeader;
            sessionStorage.setItem('scm_auth_token', authToken);
            // Poor man's role resolution just for UI demo purposes
            if (u === 'admin') authRole = 'ADMIN';
            else if (u === 'jcoordinator') authRole = 'LOGISTICS_COORDINATOR';
            else if (u === 'wmanager') authRole = 'WAREHOUSE_MANAGER';
            else if (u === 'cagent') authRole = 'CUSTOMS_AGENT';
            else authRole = 'VENDOR_REPRESENTATIVE';
            sessionStorage.setItem('scm_auth_role', authRole);
            
            displayUser.textContent = u;
            showDashboard();
        } else {
            loginError.textContent = 'Invalid credentials or access denied.';
        }
    } catch (error) {
        loginError.textContent = 'Unable to connect. Are you using HTTPS (port 8181)?';
    }
});

// --- Logout Handler ---
logoutBtn.addEventListener('click', () => {
    sessionStorage.removeItem('scm_auth_token');
    sessionStorage.removeItem('scm_auth_role');
    dashboard.classList.add('hidden');
    logoutOverlay.classList.remove('hidden');
});

// --- Navigation ---
function showDashboard() {
    loginOverlay.classList.add('hidden');
    dashboard.classList.remove('hidden');
    displayUser.textContent = authRole ? `User (${authRole})` : 'User';
    
    // RBAC: Determine allowed sections based on role
    const rolePermissions = {
        'ADMIN': ['overview', 'shipments', 'inventory', 'vendors', 'customs'],
        'LOGISTICS_COORDINATOR': ['overview', 'shipments', 'vendors', 'customs'],
        'WAREHOUSE_MANAGER': ['overview', 'inventory'],
        'CUSTOMS_AGENT': ['overview', 'shipments', 'customs'],
        'VENDOR_REPRESENTATIVE': ['overview', 'shipments', 'inventory', 'vendors'],
        'CUSTOMER': ['overview', 'shipments']
    };
    
    const allowed = rolePermissions[authRole] || ['overview'];
    
    document.querySelectorAll('.nav-item').forEach(item => {
        const target = item.getAttribute('data-target');
        if (allowed.includes(target)) {
            item.style.display = 'flex';
        } else {
            item.style.display = 'none';
        }
    });

    // Default to the first allowed view
    document.querySelector(`.nav-item[data-target="${allowed[0]}"]`).click();
}

document.querySelectorAll('.nav-item').forEach(item => {
    item.addEventListener('click', (e) => {
        e.preventDefault();
        document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
        item.classList.add('active');
        
        document.querySelectorAll('.content-section').forEach(s => s.classList.add('hidden'));
        const targetId = item.getAttribute('data-target');
        document.getElementById('section-' + targetId).classList.remove('hidden');
        
        document.getElementById('page-title').textContent = item.textContent.trim();
        
        if (targetId === 'overview') loadOverview();
        if (targetId === 'shipments') loadShipments();
        if (targetId === 'inventory') loadInventory();
        if (targetId === 'customs') loadCustoms();
    });
});

// --- API Helper ---
async function apiCall(endpoint, method = 'GET', body = null) {
    if (!authToken) {
        loginOverlay.classList.remove('hidden');
        dashboard.classList.add('hidden');
        throw new Error('Not authenticated');
    }

    const options = {
        method: method,
        headers: {
            'Authorization': authToken,
            'Accept': 'application/json'
        }
    };
    if (body) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    const res = await fetch('/scm/api' + endpoint, options);
    if (res.status === 401) {
        logoutBtn.click();
        throw new Error('Unauthorized');
    }
    if (res.status === 403) {
        throw new Error('Forbidden: You do not have the required role to perform this action.');
    }
    if (!res.ok) {
        throw new Error(`API Error: ${res.status}`);
    }
    
    // Some POST endpoints return 204 No Content
    if (res.status === 204 || res.headers.get('content-length') === '0') {
        return null;
    }
    return await res.json();
}

// --- Overview ---
async function loadOverview() {
    try {
        const shipments = await apiCall('/shipments/active');
        document.getElementById('stat-shipments').textContent = shipments.length;
    } catch (e) {
        document.getElementById('stat-shipments').textContent = 'Error';
    }
    try {
        const inventory = await apiCall('/inventory/low-stock');
        document.getElementById('stat-inventory').textContent = inventory.length;
    } catch (e) {
        document.getElementById('stat-inventory').textContent = 'Error';
    }
}

// --- Shipments ---
document.getElementById('refresh-shipments').addEventListener('click', loadShipments);
document.getElementById('btn-track').addEventListener('click', async () => {
    const tracking = document.getElementById('track-input').value;
    const resultDiv = document.getElementById('track-result');
    if (!tracking) return;
    try {
        const shipment = await apiCall(`/shipments/${tracking}`);
        resultDiv.textContent = `Status for ${tracking}: ${shipment.status} (Est: ${shipment.estimatedDelivery || 'TBD'})`;
    } catch (e) {
        resultDiv.textContent = e.message;
        resultDiv.style.color = 'var(--danger)';
    }
});

document.getElementById('btn-create-shipment').addEventListener('click', () => {
    openModal(
        'Create New Shipment',
        'Enter details to register a new shipment into the logistics network:',
        [
            { id: 'trackNum', label: 'Tracking Number', type: 'text', required: true },
            { id: 'vendorId', label: 'Vendor ID (e.g. 1)', type: 'number', required: true },
            { id: 'carrierId', label: 'Carrier ID (e.g. 1)', type: 'number', required: true },
            { id: 'origin', label: 'Origin Country Code (e.g. CN)', type: 'text', required: true },
            { id: 'dest', label: 'Destination Country Code (e.g. SE)', type: 'text', required: true },
            { id: 'weight', label: 'Weight (kg)', type: 'number', required: true },
            { id: 'estDate', label: 'Est. Delivery (YYYY-MM-DDTHH:MM:SS)', type: 'text', required: true }
        ],
        async (inputs) => {
            await apiCall('/shipments', 'POST', {
                trackingNumber: inputs.trackNum,
                vendorId: parseInt(inputs.vendorId),
                carrierId: parseInt(inputs.carrierId),
                originCountry: inputs.origin,
                destinationCountry: inputs.dest,
                weightKg: parseFloat(inputs.weight),
                estimatedDelivery: inputs.estDate
            });
            loadShipments();
        }
    );
});

async function loadShipments() {
    const tbody = document.getElementById('shipments-body');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading...</td></tr>';
    try {
        const data = await apiCall('/shipments/active');
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">No active shipments</td></tr>';
            return;
        }
        data.forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${s.trackingNumber}</td>
                <td><span class="badge" style="background: var(--primary)">${s.status}</span></td>
                <td>${s.originCountry}</td>
                <td>${s.destinationCountry}</td>
                <td>${s.estimatedDelivery || 'N/A'}</td>
                <td><button class="btn-text" style="color: var(--danger)" onclick="cancelShipment('${s.trackingNumber}')">Cancel</button></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color: var(--danger)">${e.message}</td></tr>`;
    }
}

window.cancelShipment = (trackingNumber) => {
    openModal('Cancel Shipment', `Are you sure you want to cancel shipment ${trackingNumber}?`, [], async () => {
        await apiCall(`/shipments/${trackingNumber}`, 'DELETE');
        loadShipments();
    });
};

// --- Inventory ---
document.getElementById('refresh-inventory').addEventListener('click', loadInventory);

async function loadInventory() {
    const tbody = document.getElementById('inventory-body');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading...</td></tr>';
    try {
        const data = await apiCall('/inventory/low-stock');
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">Inventory levels healthy</td></tr>';
            return;
        }
        data.forEach(item => {
            const status = item.quantityOnHand === 0 ? 'Out of Stock' : 'Low Stock';
            const statusColor = item.quantityOnHand === 0 ? 'var(--danger)' : 'var(--warning)';
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${item.sku}</td>
                <td>${item.description}</td>
                <td style="color: ${statusColor}; font-weight: bold;">${item.quantityOnHand}</td>
                <td>${item.reorderThreshold}</td>
                <td><span class="badge" style="background: ${statusColor}">${status}</span></td>
                <td><button class="btn-secondary btn-sm" onclick="replenishStock('${item.sku}')">Replenish</button></td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color: var(--danger)">${e.message}</td></tr>`;
    }
}

window.replenishStock = (sku) => {
    openModal(
        'Replenish Inventory',
        `Enter the quantity to replenish for SKU ${sku}:`,
        [
            { id: 'qty', label: 'Quantity', type: 'number', required: true },
            { id: 'ref', label: 'Source Reference (PO/Invoice)', type: 'text', required: true }
        ],
        async (inputs) => {
            await apiCall(`/inventory/${sku}/replenish`, 'POST', {
                quantity: parseInt(inputs.qty),
                sourceReference: inputs.ref
            });
            loadInventory();
        }
    );
};

// --- Vendors ---
document.getElementById('btn-assess-vendor').addEventListener('click', async () => {
    const vendorId = document.getElementById('vendor-input').value;
    const resultDiv = document.getElementById('vendor-result');
    if (!vendorId) return;
    
    try {
        resultDiv.innerHTML = '<p class="text-muted">Assessing performance...</p>';
        const data = await apiCall(`/vendors/${vendorId}/assessments`, 'POST');
        resultDiv.innerHTML = `
            <div class="glass-card" style="padding: 1rem; border-left: 4px solid var(--success);">
                <h4>Assessment Complete</h4>
                <p>Vendor Name: ${data.vendorName || 'Unknown'}</p>
                <p>Overall Score: <strong>${data.overallScore}</strong></p>
                <p>On-Time Delivery Rate: ${(data.onTimeDeliveryRate * 100).toFixed(1)}%</p>
                <p>Defect Rate: ${(data.defectRate * 100).toFixed(1)}%</p>
            </div>
        `;
    } catch (e) {
        resultDiv.innerHTML = `<p style="color: var(--danger)">${e.message}</p>`;
    }
});

// --- Customs ---
document.getElementById('refresh-customs').addEventListener('click', loadCustoms);

async function loadCustoms() {
    const tbody = document.getElementById('customs-body');
    tbody.innerHTML = '<tr><td colspan="6" class="text-center">Loading...</td></tr>';
    try {
        const data = await apiCall('/customs-documents/deadlines');
        tbody.innerHTML = '';
        if (data.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">No approaching deadlines</td></tr>';
            return;
        }
        data.forEach(doc => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${doc.documentId}</td>
                <td>${doc.documentType}</td>
                <td>${doc.shipmentId}</td>
                <td><span class="badge" style="background: var(--warning)">${doc.status}</span></td>
                <td>${doc.deadline}</td>
                <td>
                    <button class="btn-primary btn-sm" onclick="approveCustoms(${doc.documentId})">Approve</button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color: var(--danger)">${e.message}</td></tr>`;
    }
}

window.approveCustoms = (docId) => {
    openModal(
        'Approve Customs Document',
        `Please enter compliance notes for document ${docId}:`,
        [
            { id: 'notes', label: 'Compliance Notes', type: 'text', required: true }
        ],
        async (inputs) => {
            await apiCall(`/customs-documents/${docId}/approve`, 'POST', {
                complianceNotes: inputs.notes
            });
            loadCustoms();
        }
    );
};


// --- Modal System ---
function openModal(title, desc, inputsConfig, onSubmitCallback) {
    modalTitle.textContent = title;
    modalDesc.textContent = desc;
    modalError.textContent = '';
    modalInputs.innerHTML = '';
    
    inputsConfig.forEach(cfg => {
        const div = document.createElement('div');
        div.className = 'input-group';
        div.innerHTML = `
            <label for="${cfg.id}">${cfg.label}</label>
            <input type="${cfg.type}" id="${cfg.id}" required="${cfg.required}">
        `;
        modalInputs.appendChild(div);
    });
    
    currentModalAction = async (e) => {
        e.preventDefault();
        modalError.textContent = '';
        const inputs = {};
        inputsConfig.forEach(cfg => {
            inputs[cfg.id] = document.getElementById(cfg.id).value;
        });
        
        const btn = document.getElementById('modal-submit');
        const oldText = btn.textContent;
        btn.textContent = 'Processing...';
        btn.disabled = true;
        
        try {
            await onSubmitCallback(inputs);
            closeModal();
        } catch (error) {
            modalError.textContent = error.message;
        } finally {
            btn.textContent = oldText;
            btn.disabled = false;
        }
    };
    
    modalForm.removeEventListener('submit', currentModalAction); // Hack to clear old listener if it was named (it's not here, but assigning handles it)
    modalForm.onsubmit = currentModalAction;
    
    actionModal.classList.remove('hidden');
}

function closeModal() {
    actionModal.classList.add('hidden');
    currentModalAction = null;
}

modalCancel.addEventListener('click', closeModal);
