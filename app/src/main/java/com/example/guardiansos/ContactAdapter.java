package com.example.guardiansos;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

    private List<Contact> contactList;
    private OnItemClickListener itemClickListener;

    public ContactAdapter(List<Contact> contactList) {
        this.contactList = contactList;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.contact_list_item, parent, false);
        return new ContactViewHolder(view, itemClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        Contact contact = contactList.get(position);
        holder.contactName.setText(contact.getName());
        holder.contactNumber.setText(contact.getPhoneNumber());

        // Set listeners to null before setting checked state to prevent infinite loops
        holder.contactCheckbox.setOnCheckedChangeListener(null);
        holder.contactSwitch.setOnCheckedChangeListener(null);

        // Set the state from the Contact object
        holder.contactCheckbox.setChecked(contact.isSelectedForSms());
        holder.contactSwitch.setChecked(contact.isIncludedInCallQueue());

        // Now, set the listeners to update the object
        holder.contactCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            contact.setSelectedForSms(isChecked);
        });

        holder.contactSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            contact.setIncludedInCallQueue(isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return contactList.size();
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        CheckBox contactCheckbox;
        TextView contactName;
        TextView contactNumber;
        SwitchCompat contactSwitch;

        public ContactViewHolder(@NonNull View itemView, final OnItemClickListener listener) {
            super(itemView);
            contactCheckbox = itemView.findViewById(R.id.contact_checkbox);
            contactName = itemView.findViewById(R.id.contact_name);
            contactNumber = itemView.findViewById(R.id.contact_number);
            contactSwitch = itemView.findViewById(R.id.contact_switch);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        listener.onItemClick(position);
                    }
                }
            });
        }
    }
}
