-- Allow signed-in users to delete only their own notifications.
-- Keep anonymous clients from using the table-level DELETE privilege.
REVOKE DELETE ON TABLE public.user_notifications FROM anon;

-- Row ownership is enforced by RLS; the client also filters by id and user_id.
CREATE POLICY "Users delete own notifications"
ON public.user_notifications
FOR DELETE
TO authenticated
USING ((SELECT auth.uid()) = user_id);
