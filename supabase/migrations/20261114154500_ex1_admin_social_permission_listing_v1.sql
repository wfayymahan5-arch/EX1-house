create or replace function public.admin_my_permissions()
returns table(permission_code text,description text,is_allowed boolean)
language sql stable set search_path=''
as $function$
 select v.permission_code,v.description,private.has_permission(v.permission_code)
 from (values
  ('admins.manage','مدیریت مدیران'),
  ('permissions.manage','مدیریت نقش‌ها و مجوزها'),
  ('audit.view','مشاهده گزارش فعالیت مدیران'),
  ('profiles.manage','مدیریت پروفایل کاربران'),
  ('profiles.view_all','مشاهده پروفایل کاربران'),
  ('registrations.review','تأیید یا رد ثبت‌نام‌ها'),
  ('registrations.view_all','مشاهده همه ثبت‌نام‌ها'),
  ('results.manage','ثبت و ویرایش نتایج'),
  ('results.view','مشاهده نتایج'),
  ('rooms.manage','ساخت، ویرایش و مدیریت روم‌ها'),
  ('rooms.view','مشاهده روم‌ها در پنل مدیریت'),
  ('settings.manage','مدیریت تنظیمات'),
  ('settings.view','مشاهده تنظیمات'),
  ('social.moderate','مدیریت گزارش‌ها و محتوای اجتماعی'),
  ('support.manage','رسیدگی به درخواست‌های پشتیبانی'),
  ('team_members.manage','مدیریت اعضای تیم‌ها'),
  ('teams.manage','مدیریت تیم‌ها'),
  ('teams.view','مشاهده تیم‌ها'),
  ('tournaments.create','ایجاد مسابقه'),
  ('tournaments.delete','حذف مسابقه'),
  ('tournaments.update','ویرایش مسابقه'),
  ('tournaments.view','مشاهده مسابقات'),
  ('users.manage','مدیریت کاربران'),
  ('users.view','مشاهده کاربران'),
  ('wallet.demo_adjust','ثبت تغییر آزمایشی کیف پول'),
  ('wallet.view_all','مشاهده کیف پول کاربران'),
  ('wallet.view_own','مشاهده کیف پول شخصی')
 ) as v(permission_code,description)
 where auth.uid() is not null
 order by v.permission_code;
$function$;