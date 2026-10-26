/* Google Picker needs a short-lived access token in memory. Never persist or log tokens. */
(() => {
  const csrf = () => ({[document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN']: document.querySelector('meta[name="_csrf"]')?.content || ''});
  async function post(url,body,json=true) {
    const response=await fetch(url,{method:'POST',credentials:'same-origin',cache:'no-store',headers:{...csrf(),'X-Requested-With':'XMLHttpRequest',...(json?{'Content-Type':'application/json'}:{})},body:json?JSON.stringify(body):body});
    let data;try{data=await response.json();}catch{throw new Error('Phiên đã hết hạn hoặc phản hồi không hợp lệ. Tải lại trang và đăng nhập lại.');}
    if(!response.ok)throw new Error(data.error||'Không hoàn tất được thao tác.');return data;
  }
  document.querySelectorAll('input[name="ccSelf"]').forEach(checkbox=>{
    const preview=checkbox.closest('form')?.querySelector('[data-cc-preview]');
    const update=()=>{if(preview)preview.hidden=!checkbox.checked;};
    checkbox.addEventListener('change',update);update();
  });
  const form=document.getElementById('submission-form');
  if(form){form.addEventListener('submit',async e=>{e.preventDefault();if(!form.reportValidity())return;
    const button=document.getElementById('submit-button'),error=document.getElementById('form-error');error.hidden=true;button.disabled=true;button.textContent='Đang nộp bài và gửi thông báo…';
    try{const data=await post(form.action,new FormData(form),false);const target=new URL(data.redirect,location.origin);if(target.origin!==location.origin)throw new Error('Địa chỉ chuyển trang không hợp lệ.');location.assign(target.href);}
    catch(err){error.textContent=err.message;error.hidden=false;button.disabled=false;button.textContent='Nộp bài & gửi thông báo ↑';}
  });document.getElementById('file').addEventListener('change',e=>{const file=e.target.files[0];if(file)document.getElementById('file-label').textContent=file.name+' · '+(file.size/1048576).toFixed(1)+' MB';});}
  document.querySelectorAll('form[data-confirm]').forEach(f=>f.addEventListener('submit',e=>{if(!confirm(f.dataset.confirm))e.preventDefault();}));
  document.querySelectorAll('form:not(#submission-form)').forEach(f=>f.addEventListener('submit',e=>{if(e.defaultPrevented||f.method.toLowerCase()!=='post')return;const b=f.querySelector('button[type="submit"]');if(b)setTimeout(()=>{b.disabled=true;},0);}));
  let loading;
  function loadPicker(){if(loading)return loading;loading=new Promise((resolve,reject)=>{
    const begin=()=>window.gapi.load('picker',{callback:resolve,onerror:()=>{loading=undefined;reject(new Error('Không tải được Google Picker.'));},timeout:15000,ontimeout:()=>{loading=undefined;reject(new Error('Google Picker phản hồi quá lâu. Kiểm tra mạng và thử lại.'));}});
    if(window.gapi){begin();return;}const s=document.createElement('script');s.src='https://apis.google.com/js/api.js';s.async=true;s.onload=begin;s.onerror=()=>{loading=undefined;reject(new Error('Không kết nối được Google Picker.'));};document.head.appendChild(s);
  });return loading;}
  async function openFolderPicker(config,selected,title){
    if(!/^[A-Za-z0-9_-]{8,200}$/.test(config.folderId||''))throw new Error('Chưa xác định đúng thư mục đích. Không mở danh sách Drive chung.');
    await loadPicker();
    const view=new google.picker.DocsView(google.picker.ViewId.FOLDERS).setIncludeFolders(true).setSelectFolderEnabled(true).setFileIds(config.folderId);
    new google.picker.PickerBuilder().setAppId(config.appId).setDeveloperKey(config.apiKey).setOAuthToken(config.accessToken).setOrigin(location.origin)
      .setTitle(title).setSelectableMimeTypes('application/vnd.google-apps.folder').enableFeature(google.picker.Feature.NAV_HIDDEN).addView(view).setCallback(async data=>{
        if(data.action!==google.picker.Action.PICKED)return;
        try{if(data.docs?.[0]?.id!==config.folderId)throw new Error('Hãy chọn đúng thư mục đích đã cấu hình.');await selected(data.docs[0].id);}catch(problem){selected(null,problem);}
      }).build().setVisible(true);
  }
  const picker=document.getElementById('picker-button'),error=document.getElementById('picker-error');
  if(picker)picker.addEventListener('click',async()=>{error.textContent='';picker.disabled=true;try{
    const config=await post('/api/picker/credentials',{});
    await openFolderPicker(config,async(id,problem)=>{
      if(problem){error.textContent=problem.message;return;}
      try{const result=await post('/api/picker/folder',{id});location.assign(result.redirect);}catch(problem){error.textContent=problem.message;}
    },'Kết nối đúng thư mục nộp bài');
  }catch(problem){error.textContent=problem.message;}finally{picker.disabled=false;}});
  const ownerPicker=document.getElementById('drive-owner-picker'),ownerError=document.getElementById('drive-owner-error');
  if(ownerPicker)ownerPicker.addEventListener('click',async()=>{ownerError.textContent='';ownerPicker.disabled=true;try{
    const config=await post('/api/drive-setup/credentials',{});
    await openFolderPicker(config,async(id,problem)=>{
      if(problem){ownerError.textContent=problem.message;return;}
      try{const result=await post('/api/drive-setup/connect',{folderId:id,acknowledgeShared:document.getElementById('shared-access-ack')?.checked||false});location.assign(result.redirect);}catch(problem){ownerError.textContent=problem.message;}
    },'Xác nhận thư mục lớp cho chế độ tự động');
  }catch(problem){ownerError.textContent=problem.message;}finally{ownerPicker.disabled=false;}});
  const check=document.getElementById('check-folder-button');if(check)check.addEventListener('click',async()=>{error.textContent='';check.disabled=true;try{const result=await post('/api/picker/check',{});location.assign(result.redirect);}catch(e){error.textContent=e.message+' Nếu app chưa được truy cập thư mục, hãy chọn bằng Google Picker.';}finally{check.disabled=false;}});
})();
