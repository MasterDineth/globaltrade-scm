// Global State
let authHeader = '';
let currentUser = '';

// DOM Elements
const loginOverlay = document.getElementById('login-overlay');
const dashboard = document.getElementById('dashboard');
const loginForm = document.getElementById('login-form');
const loginError = document.getElementById('login-error');
const displayUser = document.getElementById('display-user');
const logoutBtn = document.getElementById('logout-btn');

const statShipments = document.getElementById('stat-shipments');
const statInventory = document.getElementById('stat-inventory');
const shipmentsBody = document.getElementById('shipments-body');
const inventoryBody = document.getElementById('inventory-body');

const refreshShipmentsBtn = document.getElementById('refresh-shipments');
const refreshInventoryBtn = document.getElementById('refresh-inventory');

// Initialization
document.addEventListener('DOMContentLoaded', () => {
    // Check if user is already logged in (session storage)
    const storedAuth = sessionStorage.getItem('authHeader');
    const storedUser = sessionStorage.getItem('currentUser');
    
    if (storedAuth && storedUser) {
        authHeader = storedAuth;
        currentUser = storedUser;
        showDashboard();
    }
});

// Login Handler
loginForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const usernameInput = document.getElementById('username').value;
    const passwordInput = document.getElementById('password').value;
    
    // Create Basic Auth Header
    const token = btoa(`${usernameInput}:${passwordInput}`);
    const tempAuthHeader = `Basic ${token}`;
    
    try {
        // Attempt to fetch active shipments to verify credentials
        const response = await fetch('/scm/api/shipments/active', {
            headers: {
                'Authorization': tempAuthHeader
            }
        });
        
        if (response.ok) {
            // Login successful
            authHeader = tempAuthHeader;
            currentUser = usernameInput;
            
            sessionStorage.setItem('authHeader', authHeader);
            sessionStorage.setItem('currentUser', currentUser);
            
            loginError.textContent = '';
            showDashboard();
        } else if (response.status === 401 || response.status === 403) {
            loginError.textContent = 'Invalid username or password.';
        } else {
            loginError.textContent = 'An error occurred. Please try again later.';
        }
    } catch (error) {
        loginError.textContent = 'Unable to connect to the server.';
        console.error('Login error:', error);
    }
});

// Logout Handler
logoutBtn.addEventListener('click', () => {
    authHeader = '';
    currentUser = '';
    sessionStorage.removeItem('authHeader');
    sessionStorage.removeItem('currentUser');
    
    loginForm.reset();
    dashboard.classList.add('hidden');
    loginOverlay.classList.add('active');
});

// View Management
function showDashboard() {
    loginOverlay.classList.remove('active');
    setTimeout(() => {
        dashboard.classList.remove('hidden');
        displayUser.textContent = currentUser;
        loadDashboardData();
    }, 300); // Wait for fade out
}

// Data Fetching
async function loadDashboardData() {
    await Promise.all([
        fetchShipments(),
        fetchInventory()
    ]);
}

refreshShipmentsBtn.addEventListener('click', fetchShipments);
refreshInventoryBtn.addEventListener('click', fetchInventory);

async function fetchShipments() {
    try {
        const response = await fetch('/scm/api/shipments/active', {
            headers: { 'Authorization': authHeader }
        });
        
        if (response.ok) {
            const shipments = await response.json();
            statShipments.textContent = shipments.length;
            renderShipments(shipments);
        } else if(response.status === 403) {
            renderShipmentsError("Access Denied: You do not have permission to view shipments.");
        }
    } catch (error) {
        console.error('Error fetching shipments:', error);
        renderShipmentsError("Failed to load shipments.");
    }
}

async function fetchInventory() {
    try {
        const response = await fetch('/scm/api/inventory/low-stock', {
            headers: { 'Authorization': authHeader }
        });
        
        if (response.ok) {
            const inventory = await response.json();
            statInventory.textContent = inventory.length;
            renderInventory(inventory);
        } else if(response.status === 403) {
             renderInventoryError("Access Denied: You do not have permission to view inventory.");
        }
    } catch (error) {
        console.error('Error fetching inventory:', error);
        renderInventoryError("Failed to load inventory.");
    }
}

// Rendering
function renderShipments(shipments) {
    if (shipments.length === 0) {
        shipmentsBody.innerHTML = '<tr><td colspan="5" class="text-center">No active shipments found.</td></tr>';
        return;
    }
    
    shipmentsBody.innerHTML = shipments.map(s => `
        <tr>
            <td><strong>${s.trackingNumber || 'N/A'}</strong></td>
            <td><span class="status-badge ${getStatusClass(s.status)}">${s.status || 'UNKNOWN'}</span></td>
            <td>${s.origin || 'N/A'}</td>
            <td>${s.destination || 'N/A'}</td>
            <td>${s.estimatedDelivery || 'Pending'}</td>
        </tr>
    `).join('');
}

function renderShipmentsError(msg) {
    shipmentsBody.innerHTML = `<tr><td colspan="5" class="text-center" style="color: var(--danger);">${msg}</td></tr>`;
}

function renderInventory(inventory) {
    if (inventory.length === 0) {
        inventoryBody.innerHTML = '<tr><td colspan="5" class="text-center">No low stock items.</td></tr>';
        return;
    }
    
    inventoryBody.innerHTML = inventory.map(item => `
        <tr>
            <td><strong>${item.sku}</strong></td>
            <td>${item.name || 'Unknown Item'}</td>
            <td>${item.quantity}</td>
            <td>${item.reorderPoint || 0}</td>
            <td><span class="status-badge ${item.quantity <= (item.reorderPoint / 2) ? 'critical' : 'low_stock'}">
                ${item.quantity <= (item.reorderPoint / 2) ? 'Critical' : 'Low Stock'}
            </span></td>
        </tr>
    `).join('');
}

function renderInventoryError(msg) {
    inventoryBody.innerHTML = `<tr><td colspan="5" class="text-center" style="color: var(--danger);">${msg}</td></tr>`;
}

// Utils
function getStatusClass(status) {
    if (!status) return '';
    const s = status.toLowerCase();
    if (s.includes('transit')) return 'in_transit';
    if (s.includes('deliver')) return 'delivered';
    if (s.includes('delay') || s.includes('exception')) return 'delayed';
    return '';
}
